package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiMeta;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiRelationshipMeta;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Ordinary domain model with an {@link Optional}-wrapped resource meta target (ADR-015). */
@JsonApiResource(type = "articles")
public record ArticleWithOptionalMeta(
    @JsonApiId String id,
    @JsonApiAttribute @Nullable String title,
    @JsonApiRelationship @Nullable ResourceIdentifier author,
    @JsonApiMeta Optional<ArticleMeta> meta,
    @JsonApiRelationshipMeta(relationship = "author") Optional<AuthorMeta> authorMeta) {}
