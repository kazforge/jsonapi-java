/**
 * JSON:API mapping orchestration and configured Jackson 3 binding mechanics.
 *
 * <p>Start with the entry point for the operation below. Its collaborators' type documentation owns
 * phase order, conversion rules, and diagnostics:
 *
 * <ul>
 *   <li>definition and property introspection: {@link MappingDefinitionCache} and {@link
 *       MappingDefinitionResolver} resolve {@link ResourceMapping} for writes/typed PATCH and
 *       {@link ReadResourceMapping} for reads/command PATCH. {@link SemanticProperty} owns roles
 *       and names; {@link MappingDefinitionInvariants} owns resource-level declaration rules;
 *   <li>resource writes: {@link DomainResourceWriter} supplies configured Jackson mechanics to
 *       {@link BasicResourceWriter} through {@link WriteResourceBackend}; {@link
 *       ResourceDecorationWriter} applies additive links;
 *   <li>compound inclusion: {@link Jackson3InclusionBackend} connects the resource writer to {@link
 *       CompoundInclusionEngine} through {@link InclusionBackend}; {@link CompoundInclusionState}
 *       tracks per-operation identities and output. {@link EffectiveRepresentation} combines
 *       selection and policy for writes and traversal;
 *   <li>flat reads: {@link DomainResourceBinder} supplies {@link ReadResourceBackend} mechanics to
 *       {@link BasicResourceReader}; {@link FlatConstructionPaths} translates construction
 *       failures. {@link TypedEnvelopeBinder} binds heterogeneous documents; {@link
 *       PrimaryDataShape} owns Level-1 primary-data shape policy;
 *   <li>PATCH: {@link DomainPatchBinder} supplies {@link PatchResourceBackend} mechanics to {@link
 *       PatchCommandBinder}; {@link DomainPatchDtoBinder} supplies {@link TypedPatchBackend}
 *       mechanics to {@link TypedPatchBinder}. Both use {@link StructuredValueBinder} and {@link
 *       StructuredPatchBinder} for recursive values; {@link PatchPresenceModule} handles typed
 *       presence markers;
 *   <li>shared mechanics: {@link RelationshipLinkageBinder} and {@link RelationshipLinkageSupport}
 *       bind linkage across reads and PATCH; {@link PropertyScopedValueConverter} preserves
 *       configured property conversion, {@link BeanConstruction} constructs DTOs, and {@link
 *       WholeMetaTarget} validates declared meta targets.
 * </ul>
 *
 * <p>These areas stay in one package because definition, conversion, construction, and linkage
 * collaborators share package-private access. The navigation groups are not architectural layers.
 * Property discovery, naming, conversion, construction, and native diagnostics use the
 * caller-configured Jackson mapper. Native token codecs live in the sibling {@code internal.codec}
 * package; neither responsibility depends on the other.
 *
 * <p>This package is unsupported implementation detail, not consumer SPI. Java-public entry points
 * allow the runtime composition package to assemble capabilities. Supported public signatures must
 * not expose these types.
 */
@NullMarked
package com.kazforge.jsonapi.jackson3.internal.mapping;

import org.jspecify.annotations.NullMarked;
