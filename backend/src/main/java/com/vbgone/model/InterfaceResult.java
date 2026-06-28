package com.vbgone.model;

public record InterfaceResult(
        String sessionId,
        String className,
        String interfaceName,
        String code,
        String implName
) {
    /**
     * Back-compat constructor: existing (C#) call sites omit implName, where the
     * implementation type name equals the className. The Java path supplies a
     * distinct implName (className + "Impl").
     */
    public InterfaceResult(String sessionId, String className, String interfaceName, String code) {
        this(sessionId, className, interfaceName, code, className);
    }
}
