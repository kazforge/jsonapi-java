package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.patch.PatchPresence;

/**
 * Shared low-level PATCH DTO whose {@code PatchPresence<T>} member wraps a presence-aware PATCH
 * shape: presence-aware PATCH shapes are a typed-path concept, so this composition fails loudly
 * with {@link com.kazforge.jsonapi.diagnostic.MappingDiagnostic#INVALID_PATCH_PROPERTY_TYPE} at the
 * attribute pointer.
 */
@JsonApiResource(type = "articles")
public record PatchPresenceAddressPatchArticle(
    @JsonApiId String id, @JsonApiAttribute PatchPresence<AddressPatch> address) {}
