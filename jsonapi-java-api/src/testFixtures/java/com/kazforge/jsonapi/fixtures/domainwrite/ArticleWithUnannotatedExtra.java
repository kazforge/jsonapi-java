package com.kazforge.jsonapi.fixtures.domainwrite;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import org.jspecify.annotations.Nullable;

/**
 * Jackson-visible extra property with no JSON:API role. It must not participate as an attribute.
 */
@JsonApiResource(type = "articles")
public record ArticleWithUnannotatedExtra(
    @JsonApiId String id, @JsonApiAttribute String title, @Nullable String ignoredExtra) {}
