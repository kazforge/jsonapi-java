package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;

/**
 * Shared low-level PATCH DTO with a scalar {@code PatchPresence<T>}-declared member, proving the
 * single-wrapper unwrap on the low-level path (ADR-014).
 */
@JsonApiResource(type = "articles")
public record PatchPresenceTitleArticle(
    @JsonApiId String id, @JsonApiAttribute PatchPresence<String> title) {}
