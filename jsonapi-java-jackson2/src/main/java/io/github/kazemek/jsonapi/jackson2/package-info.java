/**
 * Jackson 2 codecs for validating and writing JSON:API document envelopes, validated document
 * reading, advanced annotated-domain-to-resource mapping, validated flat resource-to-DTO binding,
 * and presence-aware PATCH binding.
 *
 * <p>Java {@code null} on model components means member absence. Explicit JSON {@code null} uses
 * sealed variants such as {@link io.github.kazemek.jsonapi.core.model.DocumentData.NullData}. Use
 * {@link JsonApiJackson2#writer} as the sole public writer path; the writer validates before
 * emission, so validation failure cannot leave a partially written document. Use {@link
 * JsonApiJackson2#reader} as the sole public reader path; the reader decodes token-driven wire JSON
 * through public core constructors, then runs aggregate validation before returning a document. Use
 * {@link JsonApiJackson2#resourceMapper} as the sole public write-mapping path; {@link
 * JsonApiResourceMapper} maps annotated domain values to core {@link
 * io.github.kazemek.jsonapi.core.model.ResourceObject} and {@link
 * io.github.kazemek.jsonapi.core.model.JsonApiDocument} values, keeping serialization as an
 * explicit handoff to the writer. Use {@link JsonApiJackson2#resourceBinder} as the sole public
 * flat-binding path; {@link JsonApiResourceBinder} binds already-validated {@link
 * io.github.kazemek.jsonapi.core.model.ResourceObject} values to annotated flat DTO types without
 * parsing JSON or reading document {@code included}. Use {@link JsonApiJackson2#patchCommandReader}
 * and {@link JsonApiJackson2#patchDtoReader} as the sole public PATCH paths; they force
 * update-request validation and bind only supplied members into a {@link
 * io.github.kazemek.jsonapi.jackson.patch.PatchCommand} or directly into an annotated {@link
 * io.github.kazemek.jsonapi.jackson.patch.PatchPresence} DTO.
 *
 * <p>Additional capabilities (typed envelopes and the Level-1 configured runtime) follow in later
 * parity stories; this package holds the validated document-output and document-read contracts plus
 * the advanced domain mapping in both directions and presence-aware PATCH. Cross-major parity is
 * semantic capability symmetry plus equivalent configuration authority per ADR-016, not textual
 * duplication of Jackson 3's convenience overloads.
 *
 * <p>Jackson-major adapters use a fully configured {@link
 * com.fasterxml.jackson.databind.json.JsonMapper} as the canonical construction input, followed by
 * the capability's policy/context and collaborators: {@code writer(mapper, ValidationContext)},
 * {@code reader(mapper, DocumentReadContext)}, {@code resourceMapper(mapper, identifierConverter,
 * decoratorRegistry)}, {@code resourceBinder(mapper, identifierConverter, linkageMappers)}, and
 * {@code patchCommandReader/patchDtoReader(mapper, validationContext, identifierConverter,
 * linkageMappers)} with meaningful default conveniences. {@code JsonMapper.Builder} overloads are
 * intentionally not part of the public contract. The writer derives an isolated codec mapper via
 * {@code rebuild()}; the reader uses the supplied mapper directly for token-driven parsing; the
 * resource mapper, the resource binder, and both PATCH readers each derive an isolated mapping
 * mapper via {@code rebuild()} with only mapping-required internal module support, including a
 * caller-preserving JDK 8 {@code Optional} fallback (serialization support on the mapping path,
 * deserialization support on the binding and PATCH paths). No construction path mutates the
 * caller's mapper. Jackson 2's checked {@code JsonProcessingException} mechanics propagate from
 * emission methods as-is, and every reader overload declares checked {@code IOException} while
 * Jackson parse failures surface as payload-safe {@link
 * io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiDocumentReadException} values; core
 * validation failures stay unchecked {@link
 * io.github.kazemek.jsonapi.core.validation.JsonApiValidationException} on writes and become {@code
 * JsonApiDocumentReadException} with rule codes on reads; mapping and binding failures throw {@link
 * io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException} with {@link
 * io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic} values.
 *
 * <p>Writing a {@link io.github.kazemek.jsonapi.jackson.mapping.MappedDocument} is provenance
 * aware: the writer composes its bound validation context with the mapping's sparse-fieldset
 * linkage exemptions before validating, so callers never translate mapping provenance into
 * validation policy themselves.
 *
 * <p>Codec and mapping policy, diagnostics, contexts, representation selection/policy, decoration
 * registries, provenance values, presence-aware update commands, and the opt-in identifier-meta
 * wrapper are Jackson-major-neutral contracts in {@link io.github.kazemek.jsonapi.jackson}; this
 * package holds only the Jackson 2-bound writer, reader, resource mapper, resource binder, PATCH
 * readers, and their implementations.
 */
@NullMarked
package io.github.kazemek.jsonapi.jackson2;

import org.jspecify.annotations.NullMarked;
