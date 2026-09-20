package com.kazforge.jsonapi.fixtures.domainwrite;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;

@JsonApiResource(type = "blogs")
public record BlogWithJsonProperty(
    @JsonApiId @JsonProperty("blog_id") String id,
    @JsonApiAttribute @JsonProperty("blog_title") String title) {}
