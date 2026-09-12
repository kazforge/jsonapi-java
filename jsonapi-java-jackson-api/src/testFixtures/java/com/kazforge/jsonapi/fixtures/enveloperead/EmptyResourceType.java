package com.kazforge.jsonapi.fixtures.enveloperead;

import com.kazforge.jsonapi.annotation.JsonApiResource;

/** Registry-rejection fixture: {@code @JsonApiResource} with an empty type name. */
@JsonApiResource(type = "")
public record EmptyResourceType() {}
