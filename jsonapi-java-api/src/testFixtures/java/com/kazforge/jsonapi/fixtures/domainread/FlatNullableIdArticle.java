package com.kazforge.jsonapi.fixtures.domainread;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import org.jspecify.annotations.Nullable;

/**
 * Flat read-side DTO whose nullable id role may stay unbound when no wire {@code id} is present.
 */
@JsonApiResource(type = "articles")
public record FlatNullableIdArticle(
    @JsonApiId @Nullable String id, @JsonApiAttribute @Nullable String title) {}
