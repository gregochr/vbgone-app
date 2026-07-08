import { Prism } from 'prism-react-renderer'

/**
 * Register a C# grammar on prism-react-renderer's bundled Prism.
 *
 * The vendored Prism ships only a handful of grammars (markup, clike, js/ts,
 * python, go, rust…) — `csharp` is NOT one of them. Without this, every
 * `<Highlight language="csharp">` finds no grammar and renders the whole file
 * as a single plain token, so all the generated C# showed up in one flat colour
 * (the theme's `plain` blue). Importing this module once wires up real
 * tokenisation. It's a side-effect import; guard against HMR double-registration.
 *
 * We extend the bundled `clike` grammar (comments, strings, method-call names,
 * operators, punctuation come for free) and layer the C#-specific bits on top:
 * the full keyword set, numeric literals with `m`/`f`/`d`/`u`/`l` suffixes,
 * PascalCase type names, attributes (`[TestClass]`), and verbatim/interpolated
 * strings. This covers the constrained C# the app generates (MSTest suites,
 * interfaces, stubs) without pulling in the full `prismjs` package.
 */
if (!Prism.languages.csharp) {
  Prism.languages.csharp = Prism.languages.extend('clike', {
    keyword:
      /\b(?:abstract|add|alias|as|ascending|async|await|base|bool|break|by|byte|case|catch|char|checked|class|const|continue|decimal|default|delegate|descending|do|double|dynamic|else|enum|equals|explicit|extern|false|finally|fixed|float|for|foreach|from|get|global|goto|group|if|implicit|in|init|int|interface|internal|into|is|join|let|lock|long|nameof|namespace|new|null|object|on|operator|orderby|out|override|params|partial|private|protected|public|readonly|record|ref|remove|return|sbyte|sealed|select|set|short|sizeof|stackalloc|static|string|struct|switch|this|throw|true|try|typeof|uint|ulong|unchecked|unsafe|ushort|using|value|var|virtual|void|volatile|when|where|while|with|yield)\b/,
    // decimal/hex/binary/float literals with C# suffixes (5.5m, 100m, 0x1F, 1_000L…)
    number:
      /(?:\b0x[\da-f_]+|\b0b[01_]+|(?:\b\d[\d_]*(?:\.\d[\d_]*)?|\B\.\d[\d_]+)(?:e[-+]?\d+)?)(?:[dflmu]+)?\b/i,
    operator: /\?\.|\?\?=?|::|=>|\+\+|--|&&|\|\||[-+*/%&|^!=<>]=?|[~?:]/,
    punctuation: /[{}[\];(),.:]/,
  })

  Prism.languages.insertBefore('csharp', 'keyword', {
    // Attribute names inside brackets — [TestClass], [TestMethod]. Rendered in the
    // type colour, matching how VS shows attributes. Lookbehind keeps the `[` a
    // punctuation token.
    attribute: {
      pattern: /((?:^|[^\w$])\[)[A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*/,
      lookbehind: true,
      alias: 'class-name',
    },
    // PascalCase type names: declarations (`class Foo`, `new Foo`), generic args,
    // and references followed by `.`/`)`/`,` etc. Method calls (`Foo(`) fall
    // through to clike's `function` token instead, which is what we want.
    'class-name': {
      pattern:
        /(\b(?:class|interface|struct|enum|record|namespace|new|is|as)\s+)[A-Z]\w*|\b[A-Z]\w*(?=\s*(?:<|>|\.|\)|,|;|\]|\}|\{|:|\s+[A-Za-z_]))/,
      lookbehind: true,
    },
  })

  Prism.languages.insertBefore('csharp', 'string', {
    'verbatim-string': { pattern: /@"(?:""|[^"])*"/, greedy: true, alias: 'string' },
    'interpolated-string': {
      pattern: /\$@?"(?:""|\{\{|\}\}|\\.|[^"\\])*"/,
      greedy: true,
      alias: 'string',
    },
  })

  // Common aliases so `language="cs"` also resolves.
  Prism.languages.cs = Prism.languages.csharp
}
