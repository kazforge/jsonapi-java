package com.kazforge.jsonapi.fixtures.domainpatch;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;
import java.util.List;

/** Shared direct typed PATCH DTO with built-in {@link ResourceIdentifier} relationship shapes. */
@JsonApiResource(type = "articles")
public record ArticlePatch(
    @JsonApiId String id,
    @JsonApiAttribute PatchPresence<String> title,
    @JsonApiAttribute @JsonProperty("body-text") PatchPresence<String> body,
    @JsonApiRelationship PatchPresence<ResourceIdentifier> author,
    @JsonApiRelationship PatchPresence<List<ResourceIdentifier>> comments) {}
