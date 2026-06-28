package com.vbgone.lang;

/**
 * Per-target-language identifier and file conventions.
 *
 * <p>The naming asymmetry between C# and Java is load-bearing and was previously
 * hardcoded as string concatenation (e.g. {@code "I" + className}, {@code className + ".cs"})
 * across BuildService, GenerationService and GitHubService. Centralising it here lets
 * those callers stop recomputing names and lets the Java path differ cleanly:
 * C# prefixes the interface with {@code I} and implements it on the class itself,
 * whereas Java drops the prefix and adds an {@code Impl} class.
 */
public interface LanguageConventions {

    /** Matches the request {@code targetLanguage}: {@code "csharp"} | {@code "java"}. */
    String id();

    /** Interface type name. C#: {@code I}+className. Java: className (no prefix). */
    String interfaceName(String className);

    /** Implementation type name. C#: className. Java: className+{@code Impl}. */
    String implName(String className);

    /** Test class name. C#: className+{@code Tests}. Java: className+{@code Test}. */
    String testClassName(String className);

    /** Source file extension, including the dot. {@code .cs} | {@code .java}. */
    String sourceExtension();

    /** The "not implemented" exception thrown by stubs. */
    String notImplExceptionType();
}
