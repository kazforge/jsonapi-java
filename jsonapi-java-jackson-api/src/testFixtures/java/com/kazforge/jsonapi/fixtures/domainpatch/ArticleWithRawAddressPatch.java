package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;

/**
 * Shared direct typed PATCH DTO with an invalid raw-{@code PatchPresence} nested shape (ADR-014).
 */
@JsonApiResource(type = "articles")
public record ArticleWithRawAddressPatch(
    @JsonApiId String id, @JsonApiAttribute PatchPresence<RawPresenceAddressPatch> address) {}
