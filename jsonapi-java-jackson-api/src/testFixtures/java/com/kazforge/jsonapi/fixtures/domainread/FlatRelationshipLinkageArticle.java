package com.kazforge.jsonapi.fixtures.domainread;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiRelationshipMeta;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.fixtures.domainpatch.AuthorIdMeta;
import com.kazforge.jsonapi.fixtures.domainpatch.AuthorMeta;
import com.kazforge.jsonapi.fixtures.domainpatch.CommentIdMeta;
import com.kazforge.jsonapi.fixtures.domainpatch.CommentsRelationshipMeta;
import com.kazforge.jsonapi.jackson.mapping.RelationshipLinkage;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Flat read-side DTO with opt-in {@link RelationshipLinkage} identifier meta for to-one author and
 * to-many comments, plus independent relationship-level meta at both locations.
 */
@JsonApiResource(type = "articles")
public record FlatRelationshipLinkageArticle(
    @JsonApiId String id,
    @JsonApiAttribute @Nullable String title,
    @JsonApiRelationship @Nullable RelationshipLinkage<ResourceIdentifier, AuthorIdMeta> author,
    @JsonApiRelationship
        @Nullable List<RelationshipLinkage<ResourceIdentifier, CommentIdMeta>> comments,
    @JsonApiRelationshipMeta(relationship = "author") @Nullable AuthorMeta authorMeta,
    @JsonApiRelationshipMeta(relationship = "comments")
        @Nullable CommentsRelationshipMeta commentsMeta) {}
