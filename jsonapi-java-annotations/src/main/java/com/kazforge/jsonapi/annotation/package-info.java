/**
 * Runtime-visible JSON:API domain-mapping annotations with no functional third-party runtime
 * dependencies.
 *
 * <p>These annotations declare JSON:API semantic roles only: resource type, identifier, local
 * identifier, attribute, relationship, resource-meta, and relationship-meta. They store metadata
 * only: they do not discover logical properties, name JSON:API members, validate member names,
 * convert identifiers, or request inclusion. Configured Jackson is the sole authority for property
 * discovery, visibility, external naming, mix-ins, creators, and value conversion after a property
 * is assigned a role. {@link com.kazforge.jsonapi.annotation.JsonApiResource#type()} remains
 * explicit JSON:API semantic data rather than a Jackson property name. {@code id} and {@code lid}
 * are independent JSON:API identity members mapped by distinct role annotations; neither role falls
 * back to the other. Per-linkage identifier meta is an opt-in {@code RelationshipLinkage} value,
 * not an annotation.
 *
 * <p>See ADR-004, ADR-005, ADR-007, ADR-009, and {@code docs/conformance.md}.
 */
@NullMarked
package com.kazforge.jsonapi.annotation;

import org.jspecify.annotations.NullMarked;
