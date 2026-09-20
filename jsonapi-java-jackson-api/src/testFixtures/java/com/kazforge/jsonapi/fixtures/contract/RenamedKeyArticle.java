package com.kazforge.jsonapi.fixtures.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;

/**
 * Article whose identifier property differs in all three naming concepts at once: logical property
 * name {@code articleKey}, backend external name {@code key}, and JSON:API member name {@code id}.
 * The {@code @JsonProperty} renames are test mechanics only, not contract semantics.
 */
@JsonApiResource(type = "articles")
public record RenamedKeyArticle(
    @JsonProperty("key") @JsonApiId String articleKey,
    @JsonProperty("wire-headline") @JsonApiAttribute String headline) {}
