package com.vbgone.prompt;

import com.vbgone.common.Keyed;
import com.vbgone.model.InterfaceResult;

import java.util.List;

/**
 * Per-target-language prompt strategy: system prompts, user-message construction,
 * and the post-processing of raw model output (fence stripping, declaration
 * fixing, test counting, failing-test extraction).
 *
 * <p>The C# and Java implementations differ in idioms (NUnit vs JUnit 5, namespace
 * vs package, {@code I}-prefixed interfaces vs plain) but share the same lifecycle.
 * {@code GenerationService} resolves the right implementation per request from the
 * {@code targetLanguage} and delegates to it, keeping token/cost accounting shared.
 */
public interface PromptLanguage extends Keyed {

    // ── System prompts ──

    String interfaceSystemPrompt();

    String testsSystemPrompt();

    String stubSystemPrompt();

    String implementSystemPrompt();

    // ── User messages ──

    String interfaceUserMessage(String className, String vbSource);

    String testsUserMessage(String className, String interfaceCode, String vbSource);

    String stubUserMessage(String className, InterfaceResult iface);

    String implementUserMessage(String className, InterfaceResult iface, String vbSource);

    String retryUserMessage(String className, InterfaceResult iface,
                            String previousCode, String fullTestFile,
                            List<String> failingTests, String failureDetails,
                            String failingTestSnippets);

    // ── Post-processing ──

    /** Removes language wrappers (C#: namespace blocks; Java: keeps the package line). */
    String stripWrappers(String code);

    /** Strips markdown code fences / natural-language preamble, anchoring on real code. */
    String stripCodeFences(String text);

    /** Rewrites a wrong implementation class declaration to the expected names. */
    String fixDeclaration(String code, String className, InterfaceResult iface);

    /** Repairs output truncated mid-token by balancing braces (language-neutral). */
    String repairTruncated(String code);

    /** True when the text actually looks like source code (vs analysis prose). */
    boolean looksLikeCode(String code);

    /** Counts test methods in a test file. */
    int countTests(String code);

    /** Extracts the source of the named failing test methods from the full test file. */
    String extractFailingTests(String testCode, List<String> failingTestNames);
}
