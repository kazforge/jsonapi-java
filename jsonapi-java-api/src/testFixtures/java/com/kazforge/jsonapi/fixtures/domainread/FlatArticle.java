package com.kazforge.jsonapi.fixtures.domainread;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Flat read-side DTO with built-in ResourceIdentifier relationship shapes. */
@JsonApiResource(type = "articles")
public record FlatArticle(
    @JsonApiId String id,
    @JsonApiAttribute @Nullable String title,
    @JsonApiAttribute @JsonProperty("body-text") @Nullable String body,
    @JsonApiRelationship @Nullable ResourceIdentifier author,
    @JsonApiRelationship @Nullable List<ResourceIdentifier> comments) {}
