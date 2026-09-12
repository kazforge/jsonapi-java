package com.kazforge.jsonapi.fixtures.localid;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiLocalId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import org.jspecify.annotations.Nullable;

/**
 * Passive, application-shaped related-resource carrier carrying both identity members, so linkage
 * extraction must preserve {@code id} and {@code lid} together.
 */
@JsonApiResource(type = "comments")
public record IdentifiedComment(
    @JsonApiId String id,
    @JsonApiLocalId String localId,
    @JsonApiAttribute @Nullable String body) {}
