package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;

/** Shared direct typed PATCH DTO with a nested container-inner member (atomic boundary). */
@JsonApiResource(type = "articles")
public record ArticleWithAddressTagsPatch(
    @JsonApiId String id, @JsonApiAttribute PatchPresence<AddressWithTagsPatch> address) {}
