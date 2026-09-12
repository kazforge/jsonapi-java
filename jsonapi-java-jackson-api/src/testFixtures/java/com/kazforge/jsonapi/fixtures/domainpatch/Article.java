package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;

/** Shared low-level PATCH DTO with an ordinary structured {@link Address} attribute. */
@JsonApiResource(type = "articles")
public record Article(@JsonApiId String id, @JsonApiAttribute Address address) {}
