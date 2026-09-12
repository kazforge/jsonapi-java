package com.kazforge.jsonapi.jackson.document;

import com.kazforge.jsonapi.core.model.JsonApiObject;
import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.core.model.Meta;
import org.jspecify.annotations.Nullable;

/**
 * Carries optional document-level members — links, meta, and JSON:API object — that write mapping
 * attaches to a document.
 *
 * <p>Each component may be {@code null}; absent members are omitted from the serialized document.
 */
public record DocumentEnvelope(
    @Nullable Links links, @Nullable Meta meta, @Nullable JsonApiObject jsonapi) {}
