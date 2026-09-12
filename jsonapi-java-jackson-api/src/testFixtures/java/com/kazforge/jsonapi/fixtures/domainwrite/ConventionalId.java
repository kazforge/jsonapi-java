package com.kazforge.jsonapi.fixtures.domainwrite;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiResource;

/** Uses conventional "id" property (no explicit @JsonApiId). */
@JsonApiResource(type = "conventionals")
public record ConventionalId(String id, @JsonApiAttribute String name) {}
