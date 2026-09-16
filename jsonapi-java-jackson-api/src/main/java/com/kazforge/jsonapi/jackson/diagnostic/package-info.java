/**
 * Stable document-read and mapping diagnostic families.
 *
 * <p>{@link com.kazforge.jsonapi.jackson.diagnostic.JsonApiDocumentReadException} reports JSON
 * decoding and core local or aggregate validation failures. Adapter-produced instances carry a
 * document-relative JSON Pointer ({@code ""} for the document root), a payload-safe best-effort
 * {@link com.kazforge.jsonapi.jackson.diagnostic.SourceLocation}, and a core validation rule code
 * when validation supplied one.
 *
 * <p>{@link com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException} reports domain
 * mapping, binding, registry, and representation failures through a stable {@link
 * com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic}. Its optional {@link
 * com.kazforge.jsonapi.jackson.diagnostic.MappingLocation} is a validated JSON Pointer over wire
 * names; direct resource operations use resource-relative locations and envelope composition may
 * prepend a document-relative prefix. No applicable member location is represented by {@code null},
 * never {@code ""} or {@code /}.
 *
 * <p>The families remain separate: successful document decoding followed by a domain-mapping
 * failure is not reclassified as a document-read failure.
 */
@NullMarked
package com.kazforge.jsonapi.jackson.diagnostic;

import org.jspecify.annotations.NullMarked;
