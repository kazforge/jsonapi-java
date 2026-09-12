package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import java.util.List;

/**
 * Shared low-level PATCH DTO with a nested generic {@link Box}<List<Integer>> attribute, proving a
 * nested type-variable binding (Box's {@code T} = {@code List<Integer>}) survives recursive shape
 * resolution (ADR-014).
 */
@JsonApiResource(type = "articles")
public record ArticleWithBoxList(@JsonApiId String id, @JsonApiAttribute Box<List<Integer>> box) {}
