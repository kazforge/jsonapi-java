package com.kazforge.jsonapi.fixtures.domainread;

import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import java.util.List;

/**
 * Flat read-side DTO whose ordinary relationships target a configured custom linkage-mapper type.
 */
@JsonApiResource(type = "articles")
public record FlatMappedTargetArticle(
    @JsonApiId String id,
    @JsonApiRelationship MappedReadTarget author,
    @JsonApiRelationship List<MappedReadTarget> contributors) {}
