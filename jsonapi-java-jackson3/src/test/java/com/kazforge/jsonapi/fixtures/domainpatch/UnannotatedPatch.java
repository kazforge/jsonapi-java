package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;

/**
 * Unannotated ordinary property: it does not participate in JSON:API mapping. A supplied {@code
 * note} attribute is therefore an unknown typed PATCH member.
 */
@JsonApiResource(type = "articles")
public record UnannotatedPatch(@JsonApiId String id, String note) {}
