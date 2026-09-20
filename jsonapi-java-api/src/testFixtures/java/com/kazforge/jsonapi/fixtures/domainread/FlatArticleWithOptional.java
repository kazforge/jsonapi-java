package com.kazforge.jsonapi.fixtures.domainread;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Flat read-side DTO with an Optional to-one ResourceIdentifier relationship. */
@JsonApiResource(type = "articles")
public record FlatArticleWithOptional(
    @JsonApiId String id,
    @JsonApiAttribute @Nullable String title,
    @JsonApiRelationship Optional<ResourceIdentifier> author) {}
