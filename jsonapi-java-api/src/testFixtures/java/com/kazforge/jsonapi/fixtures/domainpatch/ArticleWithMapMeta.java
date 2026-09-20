package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiMeta;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiRelationshipMeta;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Ordinary domain model with map-like resource and relationship meta targets (atomic on PATCH). */
@JsonApiResource(type = "articles")
public record ArticleWithMapMeta(
    @JsonApiId String id,
    @JsonApiAttribute @Nullable String title,
    @JsonApiRelationship @Nullable ResourceIdentifier author,
    @JsonApiMeta @Nullable Map<String, Object> meta,
    @JsonApiRelationshipMeta(relationship = "author") @Nullable Map<String, Object> authorMeta) {}
