package com.kazforge.jsonapi.fixtures.contract;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;

/**
 * Nested-attribute carrier for the shared binding-construction contract: a malformed nested value
 * proves that construction-path walking recurses through the nested bean shape and emits the
 * complete escaped JSON:API pointer rather than stopping at the top-level attribute.
 */
@JsonApiResource(type = "binding-nested")
public record BindingNestedArticle(
    @JsonApiId String id, @JsonApiAttribute BindingNestedPoint point) {}
