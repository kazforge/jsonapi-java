/**
 * JSON:API mapping orchestration and configured Jackson 3 binding mechanics.
 *
 * <p>Each area has one implementation whose type documentation owns its contract, phase order, and
 * diagnostics:
 *
 * <ul>
 *   <li>compound inclusion: {@link CompoundInclusionEngine};
 *   <li>resource writes and additive link decoration: {@link BasicResourceWriter} and {@link
 *       ResourceDecorationWriter};
 *   <li>flat resource reads: {@link BasicResourceReader}, sharing relationship-linkage binding with
 *       both PATCH paths through {@link RelationshipLinkageBinder};
 *   <li>typed-envelope document binding: {@link TypedEnvelopeBinder};
 *   <li>Level-1 primary-data shape policy: {@link PrimaryDataShape};
 *   <li>low-level PATCH commands: {@link PatchCommandBinder};
 *   <li>recursive structured values and typed PATCH DTOs: {@link StructuredPatchBinder} and {@link
 *       TypedPatchBinder};
 *   <li>mapping roles, per-property naming, and resource-level definition invariants: {@link
 *       SemanticProperty} and {@link MappingDefinitionInvariants}.
 * </ul>
 *
 * <p>Orchestration reaches backend mechanics only through narrow capability interfaces such as
 * {@link InclusionBackend}, {@link WriteResourceBackend}, {@link ReadResourceBackend}, {@link
 * PatchResourceBackend}, {@link StructuredShapeBackend}, {@link TypedPatchBackend}, and {@link
 * TypedEnvelopeBinder.Backend}. Property and type discovery, naming, conversion, construction, and
 * native diagnostics use the caller-configured Jackson mapper. Native token codecs live in the
 * sibling {@code internal.codec} package; neither responsibility depends on the other.
 *
 * <p>This package is unsupported implementation detail, not consumer SPI. Java-public entry points
 * allow the runtime composition package to assemble capabilities. Supported public signatures must
 * not expose these types.
 */
@NullMarked
package com.kazforge.jsonapi.jackson3.internal.mapping;

import org.jspecify.annotations.NullMarked;
