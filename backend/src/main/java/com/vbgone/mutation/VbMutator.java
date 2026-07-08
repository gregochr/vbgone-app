package com.vbgone.mutation;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bootstrap mutant generator for VB.NET (ADR-0001, Path B). Token-aware but text-based: it masks
 * out string literals and comments, tokenises the remaining code, and swaps one operator/literal
 * per mutant. This is the MVP generator; the ADR targets a Roslyn-VB AST generator for production —
 * this class implements {@link MutantGenerator} so that swap is a drop-in.
 *
 * <p>Being token-position based (not regex substring) it correctly distinguishes {@code <= 100}
 * from {@code <= 1000} — the exact bug the throwaway spike hit with {@code sed}.
 */
@Component
public class VbMutator implements MutantGenerator {

    /** Relational operators (longest first so the tokeniser matches {@code <=} before {@code <}). */
    private static final Map<String, String[]> RELATIONAL = new LinkedHashMap<>();
    static {
        RELATIONAL.put("<=", new String[]{"<"});   // boundary: drop the equal case
        RELATIONAL.put(">=", new String[]{">"});
        RELATIONAL.put("<>", new String[]{"="});
        RELATIONAL.put("<", new String[]{">"});     // swap
        RELATIONAL.put(">", new String[]{"<"});
    }

    private static final Map<String, String> ARITHMETIC = Map.of(
            "+", "-", "-", "+", "*", "/", "/", "*");

    // Boolean connectives are keywords; matched case-insensitively as whole word tokens.
    private static final Map<String, String> BOOLEAN = new LinkedHashMap<>();
    static {
        BOOLEAN.put("andalso", "OrElse");
        BOOLEAN.put("orelse", "AndAlso");
        BOOLEAN.put("and", "Or");
        BOOLEAN.put("or", "And");
    }

    @Override
    public List<Mutant> generate(String vbSource) {
        if (vbSource == null || vbSource.isBlank()) {
            return List.of();
        }
        List<Mutant> mutants = new ArrayList<>();
        String[] lines = vbSource.split("\n", -1);
        int offset = 0; // running char offset of the start of the current line in vbSource

        for (int lineNo = 0; lineNo < lines.length; lineNo++) {
            String line = lines[lineNo];
            boolean[] isCode = codeMask(line);
            for (Token t : tokenise(line, isCode)) {
                addMutantsForToken(mutants, vbSource, offset + t.start, t, lineNo + 1);
            }
            offset += line.length() + 1; // +1 for the '\n' consumed by split
        }
        return mutants;
    }

    private void addMutantsForToken(List<Mutant> out, String source, int globalOffset, Token t, int line) {
        String text = t.text;
        if (t.kind == Kind.OPERATOR) {
            if (RELATIONAL.containsKey(text)) {
                for (String repl : RELATIONAL.get(text)) {
                    out.add(build(source, globalOffset, text.length(), text, repl,
                            "relational", relationalDesc(text, repl), line));
                }
            } else if (ARITHMETIC.containsKey(text)) {
                String repl = ARITHMETIC.get(text);
                out.add(build(source, globalOffset, text.length(), text, repl,
                        "arithmetic", "arithmetic " + text + " → " + repl, line));
            }
        } else if (t.kind == Kind.WORD) {
            String repl = BOOLEAN.get(text.toLowerCase());
            if (repl != null) {
                out.add(build(source, globalOffset, text.length(), text, repl,
                        "boolean-connective", "boolean " + text + " → " + repl, line));
            }
        } else if (t.kind == Kind.INT_LITERAL) {
            try {
                long v = Long.parseLong(text);
                String repl = Long.toString(v + 1);
                out.add(build(source, globalOffset, text.length(), text, repl,
                        "boundary-literal", "off-by-one: " + text + " → " + repl, line));
            } catch (NumberFormatException ignored) {
                // Too big for long — skip; not worth mutating.
            }
        }
    }

    private static String relationalDesc(String before, String after) {
        boolean boundary = ("<=".equals(before) && "<".equals(after))
                || (">=".equals(before) && ">".equals(after));
        return (boundary ? "boundary " : "relational ") + before + " → " + after;
    }

    private Mutant build(String source, int offset, int len, String before, String after,
                         String operator, String description, int line) {
        String mutated = source.substring(0, offset) + after + source.substring(offset + len);
        return new Mutant(line, operator, before, after, description, mutated);
    }

    // ── Tokeniser (over the code regions of a single line) ──

    private enum Kind { OPERATOR, WORD, INT_LITERAL, OTHER }

    private record Token(int start, String text, Kind kind) {}

    /** Two-char relational/compound operators, checked before single chars. */
    private static final String[] TWO_CHAR = {"<=", ">=", "<>"};
    private static final String ONE_CHAR = "<>=+-*/";

    private List<Token> tokenise(String line, boolean[] isCode) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int n = line.length();
        while (i < n) {
            if (!isCode[i]) { i++; continue; }
            char c = line.charAt(i);

            // Word (identifier / keyword)
            if (Character.isLetter(c) || c == '_') {
                int j = i;
                while (j < n && isCode[j] && (Character.isLetterOrDigit(line.charAt(j)) || line.charAt(j) == '_')) {
                    j++;
                }
                tokens.add(new Token(i, line.substring(i, j), Kind.WORD));
                i = j;
                continue;
            }

            // Integer literal (skip decimals — a trailing '.digits' means it's not a plain int)
            if (Character.isDigit(c)) {
                int j = i;
                while (j < n && isCode[j] && Character.isDigit(line.charAt(j))) {
                    j++;
                }
                boolean isDecimal = j < n && line.charAt(j) == '.';
                Kind kind = isDecimal ? Kind.OTHER : Kind.INT_LITERAL;
                tokens.add(new Token(i, line.substring(i, j), kind));
                i = j;
                continue;
            }

            // Two-char operator
            if (i + 1 < n && isCode[i + 1]) {
                String two = line.substring(i, i + 2);
                boolean match = false;
                for (String op : TWO_CHAR) {
                    if (op.equals(two)) { match = true; break; }
                }
                if (match) {
                    tokens.add(new Token(i, two, Kind.OPERATOR));
                    i += 2;
                    continue;
                }
            }

            // Single-char operator
            if (ONE_CHAR.indexOf(c) >= 0) {
                tokens.add(new Token(i, String.valueOf(c), Kind.OPERATOR));
                i++;
                continue;
            }

            i++; // punctuation / whitespace we don't mutate
        }
        return tokens;
    }

    /**
     * Marks which characters of a line are real code (true) vs inside a string literal or a
     * trailing {@code '} comment (false). VB strings use {@code "} with {@code ""} as an escaped
     * quote.
     */
    private boolean[] codeMask(String line) {
        int n = line.length();
        boolean[] mask = new boolean[n];
        boolean inString = false;
        int i = 0;
        while (i < n) {
            char c = line.charAt(i);
            if (inString) {
                mask[i] = false;
                if (c == '"') {
                    if (i + 1 < n && line.charAt(i + 1) == '"') {
                        mask[i + 1] = false; // escaped quote — still inside the string
                        i += 2;
                        continue;
                    }
                    inString = false;
                }
                i++;
            } else {
                if (c == '"') {
                    inString = true;
                    mask[i] = false;
                    i++;
                } else if (c == '\'') {
                    // Rest of the line is a comment.
                    break;
                } else {
                    mask[i] = true;
                    i++;
                }
            }
        }
        return mask;
    }
}
