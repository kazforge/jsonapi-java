package com.kazforge.jsonapi.fixtures.compoundwrite;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import org.jspecify.annotations.Nullable;

/** Deep chain for depth-limit and nested-path tests. */
@JsonApiResource(type = "nodes")
public record DeepNode(
    @JsonApiId String id,
    @JsonApiAttribute String label,
    @JsonApiRelationship @Nullable DeepNode child) {}
