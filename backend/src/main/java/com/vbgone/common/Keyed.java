package com.vbgone.common;

/**
 * A component resolvable by a stable string id. For VBGone's language-keyed strategies the id is
 * the target language — {@code "csharp"} | {@code "java"} — matched against a request's
 * {@code targetLanguage} by an {@link AbstractLanguageRegistry}.
 */
public interface Keyed {
    String id();
}
