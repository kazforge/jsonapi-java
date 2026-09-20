package com.kazforge.jsonapi.fixtures.compoundwrite;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import java.util.List;

/** Article whose comments relationship is declared as {@link BaseComment}. */
@JsonApiResource(type = "articles")
public record PolymorphicArticle(
    @JsonApiId String id,
    @JsonApiAttribute String title,
    @JsonApiRelationship List<BaseComment> comments) {}
