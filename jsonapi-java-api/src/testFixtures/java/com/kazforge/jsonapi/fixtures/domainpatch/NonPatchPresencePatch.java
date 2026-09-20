package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;

/** Invalid direct typed PATCH DTO: a patchable member that is not {@code PatchPresence}. */
@JsonApiResource(type = "articles")
public record NonPatchPresencePatch(@JsonApiId String id, @JsonApiAttribute String title) {}
