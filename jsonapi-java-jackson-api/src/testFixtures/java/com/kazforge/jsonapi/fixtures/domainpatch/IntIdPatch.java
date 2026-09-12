package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;

/** Shared direct typed PATCH DTO with an integer identifier (identifier conversion failures). */
@JsonApiResource(type = "things")
public record IntIdPatch(@JsonApiId Integer id, @JsonApiAttribute PatchPresence<String> name) {}
