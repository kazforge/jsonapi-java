package com.kazforge.jsonapi.fixtures.localid;

import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Passive carrier for observing local and dual identity on related-resource linkage. */
@JsonApiResource(type = "articles")
public record LocalIdentityRelationshipArticle(
    @JsonApiId String id,
    @JsonApiRelationship @Nullable LocalIdOnlyComment featured,
    @JsonApiRelationship List<LocalIdOnlyComment> comments,
    @JsonApiRelationship @Nullable IdentifiedComment identified) {}
