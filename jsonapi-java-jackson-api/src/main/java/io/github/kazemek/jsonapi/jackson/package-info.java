/**
 * Public Jackson-major-neutral API surface shared by Jackson 2, Jackson 3, and future framework
 * integrations. The nested {@code io.github.kazemek.jsonapi.jackson.internal..} namespace is an
 * unsupported implementation detail shipped in this artifact for adapter cooperation and is not
 * part of this supported API description.
 *
 * <p>Contracts are grouped into concept-oriented packages:
 *
 * <ul>
 *   <li>{@code io.github.kazemek.jsonapi.jackson.document} — document contracts
 *   <li>{@code io.github.kazemek.jsonapi.jackson.mapping} — domain/mapping contracts
 *   <li>{@code io.github.kazemek.jsonapi.jackson.patch} — PATCH contracts
 *   <li>{@code io.github.kazemek.jsonapi.jackson.representation} — representation shaping
 *   <li>{@code io.github.kazemek.jsonapi.jackson.diagnostic} — diagnostics
 *   <li>{@code io.github.kazemek.jsonapi.jackson.api} — Level-1 application operation contract
 *       ({@code JsonApi} root plus resources/relationships/documents/patches facets)
 * </ul>
 *
 * <p>Mapping-diagnostic locations are major-neutral: a mapping failure carries either an absent
 * location or a valid JSON Pointer built through {@link
 * io.github.kazemek.jsonapi.jackson.diagnostic.MappingLocation}. Producers address one resource
 * object with resource-relative pointers over JSON:API member names; absence is {@code null}, never
 * {@code ""} or {@code /}; segments are individually RFC 6901-escaped. See {@link
 * io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException} for the full coordinate
 * contract.
 *
 * <p>The API is deliberately Jackson-import-free: no {@code tools.jackson.*} or {@code
 * com.fasterxml.jackson.*} type appears in any production signature. Jackson-bound readers,
 * writers, mapping introspection, serializers, binders, module registration, and mapper factories
 * stay in the major-specific adapter packages; only supported neutral values cross through the
 * public API. The Jackson-free implementation helpers under {@code jackson.internal..} are not
 * supported API and must not appear in supported public signatures.
 *
 * <p>Nullness: for document/envelope/codec contracts, Java {@code null} means member absence and
 * sealed variants represent explicit JSON {@code null}; for presence-aware PATCH, {@link
 * io.github.kazemek.jsonapi.jackson.patch.PatchChange} entries in {@code changes()} are present and
 * explicit attribute JSON {@code null} / relationship NullLinkage use {@code @Nullable value ==
 * null} (no sealed attribute-null variant). Direct PATCH DTO members declare presence through
 * {@link io.github.kazemek.jsonapi.jackson.patch.PatchPresence}, whose {@link
 * io.github.kazemek.jsonapi.jackson.patch.PatchPresence.Present} with a {@code null} value is
 * explicit null, never omission. Recursive structured attributes use {@link
 * io.github.kazemek.jsonapi.jackson.patch.StructuredPatch} / {@link
 * io.github.kazemek.jsonapi.jackson.patch.StructuredMember} (wire and logical member names) /
 * {@link io.github.kazemek.jsonapi.jackson.patch.StructuredMemberState} (Atomic / Structured) as
 * the neutral requested-change payload (ADR-014); an empty {@code StructuredPatch} is a supplied
 * empty structured object, never a clear-all. {@code @Nullable} marks intentionally null-bearing
 * members per ADR-009.
 */
@NullMarked
package io.github.kazemek.jsonapi.jackson;

import org.jspecify.annotations.NullMarked;
