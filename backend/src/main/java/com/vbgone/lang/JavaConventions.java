package com.vbgone.lang;

import org.springframework.stereotype.Component;

/** Java / JUnit 5 conventions. Interface drops the {@code I} prefix; impl gains {@code Impl}. */
@Component
public class JavaConventions implements LanguageConventions {

    @Override
    public String id() {
        return "java";
    }

    @Override
    public String interfaceName(String className) {
        return className;
    }

    @Override
    public String implName(String className) {
        return className + "Impl";
    }

    @Override
    public String testClassName(String className) {
        return className + "Test";
    }

    @Override
    public String sourceExtension() {
        return ".java";
    }

    @Override
    public String notImplExceptionType() {
        return "UnsupportedOperationException";
    }
}
