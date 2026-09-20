package com.kazforge.jsonapi.fixtures.contract;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import java.util.Map;

/**
 * Article whose {@code detail} attribute is a JSON-compatible open value containing nested explicit
 * nulls, so characterization can observe that nested null members survive mapping and binding in
 * both directions. {@code detail} may be null so the same shape also expresses an absent detail
 * attribute.
 */
@JsonApiResource(type = "articles")
public record NestedNullDetailArticle(
    @JsonApiId String id, @JsonApiAttribute Map<String, Object> detail) {}
