package com.kazforge.jsonapi.fixtures.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;

/**
 * Malformed-attribute carrier for the shared typed-envelope contract whose JSON:API wire name
 * {@code headline} differs from its Jackson logical name {@code title}. The rename is test
 * mechanics only, not contract semantics; a failure must report the wire coordinate.
 */
@JsonApiResource(type = "loc-renamed")
public record RenamedLocationArticle(
    @JsonApiId String id, @JsonApiAttribute @JsonProperty("headline") int title) {}
