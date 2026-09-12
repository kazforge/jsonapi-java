package com.kazforge.jsonapi.fixtures.enveloperead;

import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import org.jspecify.annotations.Nullable;

/** Flat read-side DTO whose relationship can reference another resource of the same type. */
@JsonApiResource(type = "nodes")
public record FlatNode(
    @JsonApiId String id, @JsonApiRelationship @Nullable ResourceIdentifier parent) {}
