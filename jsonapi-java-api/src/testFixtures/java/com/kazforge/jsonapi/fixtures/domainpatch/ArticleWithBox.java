package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;

/** Shared low-level PATCH DTO with a generic ordinary structured {@link Box}<Integer> attribute. */
@JsonApiResource(type = "articles")
public record ArticleWithBox(@JsonApiId String id, @JsonApiAttribute Box<Integer> box) {}
