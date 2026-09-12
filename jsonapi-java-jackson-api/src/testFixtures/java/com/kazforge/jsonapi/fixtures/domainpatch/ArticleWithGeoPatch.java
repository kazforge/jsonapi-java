package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;

/** Shared direct typed PATCH DTO with a multi-level presence-aware nested shape (ADR-014). */
@JsonApiResource(type = "articles")
public record ArticleWithGeoPatch(
    @JsonApiId String id, @JsonApiAttribute PatchPresence<AddressWithGeoPatch> address) {}
