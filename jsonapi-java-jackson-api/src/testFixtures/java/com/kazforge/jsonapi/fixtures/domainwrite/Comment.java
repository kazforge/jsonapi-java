package com.kazforge.jsonapi.fixtures.domainwrite;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import org.jspecify.annotations.Nullable;

@JsonApiResource(type = "comments")
public record Comment(
    @JsonApiId String id,
    @JsonApiAttribute @Nullable String body,
    @JsonApiRelationship @Nullable Person author) {}
