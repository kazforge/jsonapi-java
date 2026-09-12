package com.kazforge.jsonapi.fixtures.enveloperead;

import com.kazforge.jsonapi.annotation.JsonApiResource;

/** Registry-rejection fixture: {@code @JsonApiResource} with a member-name-invalid type name. */
@JsonApiResource(type = "no:good:type")
public record InvalidResourceType() {}
