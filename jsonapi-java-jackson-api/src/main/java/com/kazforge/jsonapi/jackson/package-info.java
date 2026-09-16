/**
 * Supported Jackson-major-neutral contracts shared by the Jackson 2 and Jackson 3 adapters and by
 * framework integrations.
 *
 * <p>{@link com.kazforge.jsonapi.jackson.api.JsonApi} is the Level-1 application entry point for
 * ordinary resource, relationship, document, and PATCH operations. Major-specific adapters retain
 * the advanced APIs for Jackson-native types, mapper and module setup, heterogeneous binding, and
 * explicit codec or mapping composition.
 *
 * <p>Supporting contracts are grouped by responsibility: document context and envelopes in {@code
 * jackson.document}, domain mapping in {@code jackson.mapping}, requested changes in {@code
 * jackson.patch}, output shaping in {@code jackson.representation}, and stable failures in {@code
 * jackson.diagnostic}.
 *
 * <p>No supported signature imports {@code tools.jackson.*}, {@code com.fasterxml.jackson.*}, or a
 * major-specific adapter package. The {@code com.kazforge.jsonapi.jackson.internal} namespace is an
 * unsupported implementation detail shipped for adapter cooperation; its Java-public types are not
 * supported API and must not appear in supported public signatures.
 *
 * <p>Wire-visible presence is preserved. On document and envelope values, Java {@code null} means
 * that a member is absent and core sealed variants represent explicit JSON {@code null}. PATCH DTOs
 * use {@link com.kazforge.jsonapi.jackson.patch.PatchPresence}; a {@link
 * com.kazforge.jsonapi.jackson.patch.PatchPresence.Present} value is supplied even when its
 * converted value is {@code null}. Low-level structured changes use {@link
 * com.kazforge.jsonapi.jackson.patch.StructuredPatch}; missing nested members are omitted changes,
 * while an empty structured patch is a supplied empty object, not a clear-all operation.
 */
@NullMarked
package com.kazforge.jsonapi.jackson;

import org.jspecify.annotations.NullMarked;
