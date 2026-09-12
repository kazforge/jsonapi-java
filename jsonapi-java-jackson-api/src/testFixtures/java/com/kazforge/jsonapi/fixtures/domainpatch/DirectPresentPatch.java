package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;

/** Invalid direct typed PATCH DTO: a member typed as the concrete {@code Present} variant. */
@JsonApiResource(type = "articles")
public record DirectPresentPatch(
    @JsonApiId String id, @JsonApiAttribute PatchPresence.Present<String> title) {}
