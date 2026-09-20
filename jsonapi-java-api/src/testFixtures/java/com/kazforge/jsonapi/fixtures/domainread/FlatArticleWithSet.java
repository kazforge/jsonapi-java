package com.kazforge.jsonapi.fixtures.domainread;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Flat read-side DTO with a Set-based to-many ResourceIdentifier relationship. */
@JsonApiResource(type = "articles")
public record FlatArticleWithSet(
    @JsonApiId String id,
    @JsonApiAttribute @Nullable String title,
    @JsonApiRelationship @Nullable Set<ResourceIdentifier> tags) {}
