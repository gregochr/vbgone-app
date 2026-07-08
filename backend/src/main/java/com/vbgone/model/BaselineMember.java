package com.vbgone.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One public member of the pinned baseline surface (Assure step 3).
 *
 * @param signature the member signature, e.g. "decimal CalculateTotal(IReadOnlyList&lt;LineItem&gt; items)"
 * @param defect    amber defect tag shown when the analysis flagged this member; null otherwise
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BaselineMember(
        String signature,
        String defect
) {}
