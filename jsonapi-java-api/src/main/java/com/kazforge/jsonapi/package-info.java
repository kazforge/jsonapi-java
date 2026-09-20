/**
 * Supported backend-independent contracts shared by the Jackson 2 and Jackson 3 adapters and by
 * framework integrations.
 *
 * <p>{@link com.kazforge.jsonapi.api.JsonApi} is the Level-1 application entry point for ordinary
 * resource, relationship, document, and PATCH operations. Major-specific adapters retain the
 * advanced APIs for Jackson-native types, mapper and module setup, heterogeneous binding, and
 * explicit codec or mapping composition.
 *
 * <p>Supporting contracts are grouped by responsibility: document context and envelopes in {@code
 * document}, domain mapping in {@code mapping}, requested changes in {@code patch}, output shaping
 * in {@code representation}, and stable failures in {@code diagnostic}.
 *
 * <p>No supported signature imports {@code tools.jackson.*}, {@code com.fasterxml.jackson.*}, or a
 * major-specific adapter package. The current Jackson 2 and Jackson 3 adapters derive observable
 * property semantics through caller-configured Jackson (discovery, visibility, external names,
 * construction, and conversion); native Jackson mechanics stay in each adapter. The {@code
 * com.kazforge.jsonapi.internal} namespace is an unsupported implementation detail shipped for
 * adapter cooperation; its Java-public types are not supported API and must not appear in supported
 * public signatures.
 *
 * <p>Wire-visible presence is preserved. On document and envelope values, Java {@code null} means
 * that a member is absent and core sealed variants represent explicit JSON {@code null}. PATCH DTOs
 * use {@link com.kazforge.jsonapi.patch.PatchPresence}; a {@link
 * com.kazforge.jsonapi.patch.PatchPresence.Present} value is supplied even when its converted value
 * is {@code null}. Low-level structured changes use {@link
 * com.kazforge.jsonapi.patch.StructuredPatch}; missing nested members are omitted changes, while an
 * empty structured patch is a supplied empty object, not a clear-all operation.
 */
@NullMarked
package com.kazforge.jsonapi;

import org.jspecify.annotations.NullMarked;
