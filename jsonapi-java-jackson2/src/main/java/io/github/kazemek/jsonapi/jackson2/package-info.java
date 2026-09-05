/**
 * Jackson 2 codecs for validating and writing JSON:API document envelopes.
 *
 * <p>Java {@code null} on model components means member absence. Explicit JSON {@code null} uses
 * sealed variants such as {@link io.github.kazemek.jsonapi.core.model.DocumentData.NullData}. Use
 * {@link JsonApiJackson2#writer} as the sole public codec path; the writer validates before
 * emission, so validation failure cannot leave a partially written document.
 *
 * <p>Additional capabilities (document reading, domain mapping, flat binding, presence-aware PATCH,
 * and the Level-1 configured runtime) follow in later parity stories; this package holds only the
 * validated document-output contract. Cross-major parity is semantic capability symmetry plus
 * equivalent configuration authority per ADR-016, not textual duplication of Jackson 3's
 * convenience overloads.
 *
 * <p>Jackson-major adapters use a fully configured {@link
 * com.fasterxml.jackson.databind.json.JsonMapper} as the canonical construction input, followed by
 * the capability's policy/context: {@code writer(mapper, ValidationContext)}, with a
 * documented-default convenience form delegating to it. {@code JsonMapper.Builder} overloads are
 * intentionally not part of the public contract. The writer derives an isolated codec mapper via
 * {@code rebuild()} and never mutates the caller's mapper. Jackson 2's checked {@code
 * JsonProcessingException} mechanics propagate from emission methods as-is; core validation
 * failures stay unchecked {@link
 * io.github.kazemek.jsonapi.core.validation.JsonApiValidationException}.
 *
 * <p>Writing a {@link io.github.kazemek.jsonapi.jackson.mapping.MappedDocument} is provenance
 * aware: the writer composes its bound validation context with the mapping's sparse-fieldset
 * linkage exemptions before validating, so callers never translate mapping provenance into
 * validation policy themselves.
 *
 * <p>Codec policy, diagnostics, contexts, and provenance values are Jackson-major-neutral contracts
 * in {@link io.github.kazemek.jsonapi.jackson}; this package holds only the Jackson 2-bound writer
 * and its implementation.
 */
@NullMarked
package io.github.kazemek.jsonapi.jackson2;

import org.jspecify.annotations.NullMarked;
