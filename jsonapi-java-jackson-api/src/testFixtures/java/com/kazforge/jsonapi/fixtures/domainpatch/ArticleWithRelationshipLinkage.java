package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiRelationshipMeta;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.jackson.mapping.RelationshipLinkage;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Shared ordinary domain model with opt-in per-linkage identifier meta on to-one author and to-many
 * comments, plus independent relationship-level meta at both locations (ADR-017).
 */
@JsonApiResource(type = "articles")
public record ArticleWithRelationshipLinkage(
    @JsonApiId String id,
    @JsonApiAttribute @Nullable String title,
    @JsonApiRelationship @Nullable RelationshipLinkage<ResourceIdentifier, AuthorIdMeta> author,
    @JsonApiRelationship
        @Nullable List<RelationshipLinkage<ResourceIdentifier, CommentIdMeta>> comments,
    @JsonApiRelationshipMeta(relationship = "author") @Nullable AuthorMeta authorMeta,
    @JsonApiRelationshipMeta(relationship = "comments")
        @Nullable CommentsRelationshipMeta commentsMeta) {}
