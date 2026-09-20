package com.kazforge.jsonapi.fixtures.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiLocalId;
import com.kazforge.jsonapi.annotation.JsonApiResource;

/**
 * Article whose local identifier property carries a backend external name other than {@code lid},
 * so characterization can observe that the backend rename never moves the JSON:API {@code lid}
 * member. The {@code @JsonProperty} rename is test mechanics only, not contract semantics.
 */
@JsonApiResource(type = "articles")
public record RenamedLidArticle(
    @JsonApiId String id,
    @JsonProperty("wire-local-id") @JsonApiLocalId String localId,
    @JsonApiAttribute String title) {}
