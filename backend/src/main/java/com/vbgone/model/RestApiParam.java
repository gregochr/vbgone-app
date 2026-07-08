package com.vbgone.model;

/**
 * One input to a detected web API endpoint — a route token or a query-string value.
 *
 * @param name the parameter name
 * @param in   where the value comes from: {@code "path"} or {@code "query"}
 * @param type its declared type
 * @param note a short plain-language hint
 */
public record RestApiParam(
        String name,
        String in,
        String type,
        String note
) {}
