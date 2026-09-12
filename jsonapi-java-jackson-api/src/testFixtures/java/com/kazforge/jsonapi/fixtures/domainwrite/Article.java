package com.kazforge.jsonapi.fixtures.domainwrite;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import java.util.List;
import org.jspecify.annotations.Nullable;

@JsonApiResource(type = "articles")
public record Article(
    @JsonApiId String id,
    @JsonApiAttribute String title,
    @JsonApiAttribute @JsonProperty("body-text") String body,
    @JsonApiRelationship List<Comment> comments,
    @JsonApiRelationship @Nullable Person author) {}
