package com.vbgone.model;

import java.util.List;

/**
 * A web API endpoint spotted statically in the source — an ASP.NET Web API action or an ASMX
 * web method. Assure can't wrap these in a safety net yet, so they're reported as a separate,
 * parallel list rather than folded into the readiness buckets.
 *
 * @param verb      HTTP verb (GET/POST/PUT/PATCH/DELETE)
 * @param route     route template, e.g. {@code "/api/orders/{id}"}
 * @param handler   the {@code Class.Method} that handles the call
 * @param source    the VB.NET file the endpoint was found in
 * @param kind      how it was detected: {@code "Web API"} or {@code "ASMX"}
 * @param params    path/query inputs
 * @param reqType   request-body type name, or {@code "—"} when there is none
 * @param req       sample request body, or {@code null} when the endpoint takes no body
 * @param resType   response type name
 * @param resStatus HTTP status the handler returns, e.g. {@code "200 OK"}
 * @param res       sample response body ({@code ""} when a sample wasn't generated)
 */
public record RestApiEndpoint(
        String verb,
        String route,
        String handler,
        String source,
        String kind,
        List<RestApiParam> params,
        String reqType,
        String req,
        String resType,
        String resStatus,
        String res
) {}
