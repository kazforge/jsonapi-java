package com.kazforge.jsonapi.fixtures.domainread;

import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.fixtures.domainpatch.AuthorIdMeta;
import com.kazforge.jsonapi.fixtures.domainpatch.CommentIdMeta;
import com.kazforge.jsonapi.mapping.RelationshipLinkage;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Flat read-side DTO with opt-in {@link RelationshipLinkage} over a configured custom
 * linkage-mapper target, for identifier-meta pairing independent of the built-in identifier shape.
 */
@JsonApiResource(type = "articles")
public record FlatWrappedMappedTargetArticle(
    @JsonApiId String id,
    @JsonApiRelationship @Nullable RelationshipLinkage<MappedReadTarget, AuthorIdMeta> author,
    @JsonApiRelationship
        @Nullable List<RelationshipLinkage<MappedReadTarget, CommentIdMeta>> comments) {}
