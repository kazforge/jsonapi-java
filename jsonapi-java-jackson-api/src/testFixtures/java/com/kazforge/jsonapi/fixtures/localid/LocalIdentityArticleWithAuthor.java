package com.kazforge.jsonapi.fixtures.localid;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiLocalId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.fixtures.domainwrite.Comment;
import com.kazforge.jsonapi.fixtures.domainwrite.Person;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Passive, application-shaped carrier with independent identity roles plus ordinary relationships,
 * for create-authoring paths where the primary may carry no wire identity while its related
 * resources stay identified.
 */
@JsonApiResource(type = "articles")
public record LocalIdentityArticleWithAuthor(
    @JsonApiId @Nullable String id,
    @JsonApiLocalId @Nullable String localId,
    @JsonApiAttribute @Nullable String title,
    @JsonApiRelationship @Nullable Person author,
    @JsonApiRelationship List<Comment> comments) {}
