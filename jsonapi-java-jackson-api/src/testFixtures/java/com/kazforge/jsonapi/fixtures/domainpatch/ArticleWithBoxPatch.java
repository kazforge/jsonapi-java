package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;

/** Shared direct typed PATCH DTO with a generic nested {@link BoxPatch}<Integer> member. */
@JsonApiResource(type = "articles")
public record ArticleWithBoxPatch(
    @JsonApiId String id, @JsonApiAttribute PatchPresence<BoxPatch<Integer>> box) {}
