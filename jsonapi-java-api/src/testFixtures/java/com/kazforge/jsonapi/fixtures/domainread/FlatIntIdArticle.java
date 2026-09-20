package com.kazforge.jsonapi.fixtures.domainread;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import org.jspecify.annotations.Nullable;

/** Flat read-side DTO with a non-String identifier coerced via convertValue. */
@JsonApiResource(type = "articles")
public record FlatIntIdArticle(
    @JsonApiId @Nullable Integer id, @JsonApiAttribute @Nullable String title) {}
