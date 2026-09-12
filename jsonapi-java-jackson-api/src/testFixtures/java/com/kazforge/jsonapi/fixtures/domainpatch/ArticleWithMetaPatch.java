package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiMeta;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiRelationshipMeta;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;

/** Shared typed PATCH DTO with recursive resource meta and relationship meta members. */
@JsonApiResource(type = "articles")
public record ArticleWithMetaPatch(
    @JsonApiId String id,
    @JsonApiAttribute PatchPresence<String> title,
    @JsonApiRelationship PatchPresence<ResourceIdentifier> author,
    @JsonApiMeta PatchPresence<ArticleMetaPatch> meta,
    @JsonApiRelationshipMeta(relationship = "author") PatchPresence<AuthorMeta> authorMeta) {}
