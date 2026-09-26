/**
 * Backend-neutral JSON:API mapping semantics shared by the backend runtimes.
 *
 * <p>Each area has one neutral implementation whose type documentation owns its contract, phase
 * order, and diagnostics:
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
 * native diagnostics stay with each backend.
 *
 * <p>This package is not consumer SPI. Its Java-public types exist only so backend artifacts can
 * cooperate on neutral mapping implementation. Application code must not depend on it, and backend
 * supported public signatures must not expose it.
 */
@NullMarked
package com.kazforge.jsonapi.mapping.internal;

import org.jspecify.annotations.NullMarked;
