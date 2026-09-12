package com.kazforge.jsonapi.fixtures.sparsefieldset;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.fixtures.domainwrite.Person;

/** Article whose author relationship uses a renamed JSON:API member. */
@JsonApiResource(type = "articles")
public record ArticleWithRenamedAuthor(
    @JsonApiId String id,
    @JsonApiAttribute String title,
    @JsonApiRelationship @JsonProperty("written-by") Person author) {}
