package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.jackson.mapping.RelationshipLinkage;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;
import java.util.List;

/**
 * Typed PATCH DTO that carries identifier meta only as part of whole-linkage {@link
 * RelationshipLinkage} replacement (ADR-017).
 */
@JsonApiResource(type = "articles")
public record ArticleWithRelationshipLinkagePatch(
    @JsonApiId String id,
    @JsonApiAttribute PatchPresence<String> title,
    @JsonApiRelationship
        PatchPresence<RelationshipLinkage<ResourceIdentifier, AuthorIdMeta>> author,
    @JsonApiRelationship
        PatchPresence<List<RelationshipLinkage<ResourceIdentifier, CommentIdMeta>>> comments) {}
