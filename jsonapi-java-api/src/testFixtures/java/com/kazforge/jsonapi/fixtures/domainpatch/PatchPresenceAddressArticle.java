package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.patch.PatchPresence;

/**
 * Shared low-level PATCH DTO whose {@code PatchPresence<T>}-declared member wraps an ordinary
 * structured bean, proving the single-wrapper unwrap recurses on the low-level path.
 */
@JsonApiResource(type = "articles")
public record PatchPresenceAddressArticle(
    @JsonApiId String id, @JsonApiAttribute PatchPresence<Address> address) {}
