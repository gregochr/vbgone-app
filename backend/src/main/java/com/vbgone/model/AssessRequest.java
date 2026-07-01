package com.vbgone.model;

/**
 * Portfolio Readiness Assessment request. A static (no-AI) pass classifies the supplied
 * VB.NET source; nothing is sent to a model.
 *
 * @param filename source file name (for the report's file paths)
 * @param content  VB.NET source — may contain multiple classes
 */
public record AssessRequest(
        String filename,
        String content
) {}
