package com.kazforge.jsonapi.fixtures.enveloperead;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;

/** Flat read-side DTO whose attribute conversion can fail for non-numeric wire values. */
@JsonApiResource(type = "strict-articles")
public record FlatStrictArticle(@JsonApiId String id, @JsonApiAttribute int title) {}
