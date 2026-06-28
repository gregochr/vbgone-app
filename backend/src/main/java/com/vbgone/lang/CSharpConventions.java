package com.vbgone.lang;

import org.springframework.stereotype.Component;

/** C# / NUnit conventions — preserves today's behaviour exactly. */
@Component
public class CSharpConventions implements LanguageConventions {

    @Override
    public String id() {
        return "csharp";
    }

    @Override
    public String interfaceName(String className) {
        return "I" + className;
    }

    @Override
    public String implName(String className) {
        return className;
    }

    @Override
    public String testClassName(String className) {
        return className + "Tests";
    }

    @Override
    public String sourceExtension() {
        return ".cs";
    }

    @Override
    public String notImplExceptionType() {
        return "NotImplementedException";
    }
}
