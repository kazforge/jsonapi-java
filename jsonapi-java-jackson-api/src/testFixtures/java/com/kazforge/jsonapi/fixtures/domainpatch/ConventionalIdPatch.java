package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;

/**
 * Conventional {@code id} property with no {@code @JsonApiId}: the sole implicit JSON:API
 * property-role convention.
 */
@JsonApiResource(type = "articles")
public record ConventionalIdPatch(String id, @JsonApiAttribute PatchPresence<String> title) {}
