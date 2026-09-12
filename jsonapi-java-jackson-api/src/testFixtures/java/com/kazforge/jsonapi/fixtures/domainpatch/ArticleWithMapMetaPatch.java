package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiMeta;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiRelationshipMeta;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;
import java.util.Map;

/** Shared typed PATCH DTO with atomic map-like resource meta (PATCH stays atomic under ADR-015). */
@JsonApiResource(type = "articles")
public record ArticleWithMapMetaPatch(
    @JsonApiId String id,
    @JsonApiAttribute PatchPresence<String> title,
    @JsonApiRelationship PatchPresence<ResourceIdentifier> author,
    @JsonApiMeta PatchPresence<Map<String, Object>> meta,
    @JsonApiRelationshipMeta(relationship = "author")
        PatchPresence<Map<String, Object>> authorMeta) {}
