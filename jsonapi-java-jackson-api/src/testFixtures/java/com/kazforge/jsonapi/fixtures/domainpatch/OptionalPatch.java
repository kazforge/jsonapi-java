package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;
import java.util.Optional;

/**
 * Shared direct typed PATCH DTO proving presence is separate from inner {@link Optional} nullness.
 */
@JsonApiResource(type = "articles")
public record OptionalPatch(
    @JsonApiId String id, @JsonApiAttribute PatchPresence<Optional<String>> subtitle) {}
