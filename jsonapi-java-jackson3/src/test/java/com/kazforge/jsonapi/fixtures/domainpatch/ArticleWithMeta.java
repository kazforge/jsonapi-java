package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiMeta;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiRelationshipMeta;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import org.jspecify.annotations.Nullable;

/** Shared ordinary domain model with whole-object resource and relationship meta. */
@JsonApiResource(type = "articles")
public record ArticleWithMeta(
    @JsonApiId String id,
    @JsonApiAttribute @Nullable String title,
    @JsonApiRelationship @Nullable ResourceIdentifier author,
    @JsonApiMeta @Nullable ArticleMeta meta,
    @JsonApiRelationshipMeta(relationship = "author") @Nullable AuthorMeta authorMeta) {}
