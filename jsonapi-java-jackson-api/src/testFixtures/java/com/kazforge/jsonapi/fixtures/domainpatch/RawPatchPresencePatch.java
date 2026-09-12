package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;

/** Invalid direct typed PATCH DTO: a raw (unparameterized) {@code PatchPresence} member. */
@JsonApiResource(type = "articles")
@SuppressWarnings("rawtypes")
public record RawPatchPresencePatch(@JsonApiId String id, @JsonApiAttribute PatchPresence title) {}
