package com.kazforge.jsonapi.fixtures.domainwrite;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import org.jspecify.annotations.Nullable;

@JsonApiResource(type = "people")
public record Person(@JsonApiId String id, @JsonApiAttribute @Nullable String name) {}
