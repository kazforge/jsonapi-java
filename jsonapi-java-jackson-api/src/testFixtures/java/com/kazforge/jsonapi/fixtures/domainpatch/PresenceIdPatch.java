package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;

/**
 * Invalid direct typed PATCH DTO: the identifier must never be a patchable {@code PatchPresence}.
 */
@JsonApiResource(type = "articles")
public record PresenceIdPatch(@JsonApiId PatchPresence<String> id) {}
