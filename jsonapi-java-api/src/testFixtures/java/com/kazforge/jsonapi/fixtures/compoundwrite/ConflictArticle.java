package com.kazforge.jsonapi.fixtures.compoundwrite;

import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.fixtures.domainwrite.Person;

/** Article with two to-one people relationships for conflicting-representation tests. */
@JsonApiResource(type = "articles")
public record ConflictArticle(
    @JsonApiId String id,
    @JsonApiRelationship Person author,
    @JsonApiRelationship Person reviewer) {}
