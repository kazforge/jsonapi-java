package com.kazforge.jsonapi.fixtures.contract;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;

/**
 * Creator-only attribute carrier for the shared binding-construction contract: Jackson binds the
 * immutable record components through the effective canonical creator without any explicit
 * annotation beyond the JSON:API roles, so both adapters exercise the same neutral carrier.
 */
@JsonApiResource(type = "binding-creator")
public record BindingCreatorOnlyArticle(@JsonApiId String id, @JsonApiAttribute String title) {}
