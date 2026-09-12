package com.kazforge.jsonapi.fixtures.domainread;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiMeta;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiRelationshipMeta;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleMeta;
import com.kazforge.jsonapi.fixtures.domainpatch.AuthorMeta;
import org.jspecify.annotations.Nullable;

/** Flat read-side DTO with whole-object resource and relationship meta. */
@JsonApiResource(type = "articles")
public record FlatMetaArticle(
    @JsonApiId String id,
    @JsonApiAttribute @Nullable String title,
    @JsonApiRelationship @Nullable ResourceIdentifier author,
    @JsonApiMeta @Nullable ArticleMeta meta,
    @JsonApiRelationshipMeta(relationship = "author") @Nullable AuthorMeta authorMeta) {}
