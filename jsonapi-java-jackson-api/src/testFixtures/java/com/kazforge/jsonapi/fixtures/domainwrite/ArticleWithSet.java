package com.kazforge.jsonapi.fixtures.domainwrite;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import java.util.Set;

@JsonApiResource(type = "articles")
public record ArticleWithSet(
    @JsonApiId String id, @JsonApiAttribute String title, @JsonApiRelationship Set<Tag> tags) {}
