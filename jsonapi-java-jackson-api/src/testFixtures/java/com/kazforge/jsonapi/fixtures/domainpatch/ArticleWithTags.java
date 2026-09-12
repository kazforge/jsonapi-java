package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import java.util.List;

/** Shared low-level PATCH DTO with a container attribute, proving atomic container boundaries. */
@JsonApiResource(type = "articles")
public record ArticleWithTags(@JsonApiId String id, @JsonApiAttribute List<String> tags) {}
