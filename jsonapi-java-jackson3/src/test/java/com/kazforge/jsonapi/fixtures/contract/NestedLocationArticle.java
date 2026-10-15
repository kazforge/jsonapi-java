package com.kazforge.jsonapi.fixtures.contract;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;

/**
 * Nested-attribute carrier for the shared typed-envelope contract: a malformed nested value proves
 * that document-prefix composition recurses through the nested bean shape and emits the complete
 * escaped JSON:API pointer under {@code /data}.
 */
@JsonApiResource(type = "loc-nested")
public record NestedLocationArticle(
    @JsonApiId String id, @JsonApiAttribute NestedAddress address) {}
