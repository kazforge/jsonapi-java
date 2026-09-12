package com.kazforge.jsonapi.fixtures.compoundwrite;

import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.fixtures.domainpatch.AuthorIdMeta;
import com.kazforge.jsonapi.fixtures.domainpatch.CommentIdMeta;
import com.kazforge.jsonapi.fixtures.domainwrite.Comment;
import com.kazforge.jsonapi.fixtures.domainwrite.Person;
import com.kazforge.jsonapi.jackson.mapping.RelationshipLinkage;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Compound-inclusion graph whose relationship targets are wrapped in {@link RelationshipLinkage} so
 * include traversal walks the unwrapped {@link Person} and {@link Comment} resources (ADR-017).
 */
@JsonApiResource(type = "articles")
public record WrappedLinkageArticle(
    @JsonApiId String id,
    @JsonApiRelationship @Nullable RelationshipLinkage<Person, AuthorIdMeta> author,
    @JsonApiRelationship @Nullable List<RelationshipLinkage<Comment, CommentIdMeta>> comments) {}
