package com.kazforge.jsonapi.jackson.document;

import com.kazforge.jsonapi.core.model.JsonApiObject;
import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.core.model.Meta;
import org.jspecify.annotations.Nullable;

/**
 * Optional top-level links, meta, and JSON:API object attached during domain mapping.
 *
 * <p>A {@code null} component means that member is absent and is omitted from the serialized
 * document. A non-null empty value remains present: for example, {@link Links#empty()} and {@link
 * Meta#empty()} produce empty {@code links} and {@code meta} objects rather than absence.
 */
public record DocumentEnvelope(
    @Nullable Links links, @Nullable Meta meta, @Nullable JsonApiObject jsonapi) {}
