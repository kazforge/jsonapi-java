package com.kazforge.jsonapi.fixtures.compoundwrite;

import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import org.jspecify.annotations.Nullable;

/** Self-referential article for primary-as-related inclusion tests. */
@JsonApiResource(type = "articles")
public record LinkedArticle(
    @JsonApiId String id, @JsonApiRelationship @Nullable LinkedArticle related) {}
