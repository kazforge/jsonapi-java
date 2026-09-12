package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;

/**
 * Direct typed PATCH DTO wrapping a JavaBean-style nested {@link MutableAddressPatch} shape,
 * proving typed-path recursion applies to ordinary Jackson-bean semantics, not records specifically
 * (ADR-014).
 */
@JsonApiResource(type = "articles")
public record MutableArticleWithAddressPatch(
    @JsonApiId String id, @JsonApiAttribute PatchPresence<MutableAddressPatch> address) {}
