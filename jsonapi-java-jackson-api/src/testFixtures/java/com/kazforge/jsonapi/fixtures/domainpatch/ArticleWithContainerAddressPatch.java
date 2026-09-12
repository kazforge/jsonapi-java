package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;

/** Shared direct typed PATCH DTO with {@code Set}/{@code array}/{@code Map} nested members. */
@JsonApiResource(type = "articles")
public record ArticleWithContainerAddressPatch(
    @JsonApiId String id, @JsonApiAttribute PatchPresence<AddressWithContainersPatch> address) {}
