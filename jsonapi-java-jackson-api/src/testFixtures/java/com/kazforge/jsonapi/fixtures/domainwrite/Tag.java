package com.kazforge.jsonapi.fixtures.domainwrite;

import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;

@JsonApiResource(type = "tags")
public record Tag(@JsonApiId String name) {}
