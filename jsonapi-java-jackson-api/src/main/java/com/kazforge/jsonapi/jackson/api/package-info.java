/**
 * Major-neutral Level-1 JSON:API application operations.
 *
 * <p>Level 1 is the ordinary application path: small, cohesive, client/server-neutral operations
 * over JSON:API semantics. Advanced capability APIs (document readers/writers, resource
 * mapper/binder, {@code JavaType} overloads, heterogeneous domain envelopes, low-level document
 * contexts) remain the explicit mechanism/control seams in the major-specific adapters and are
 * unchanged by this package.
 *
 * <p>The root {@link com.kazforge.jsonapi.jackson.api.JsonApi} exposes four facets: {@link
 * com.kazforge.jsonapi.jackson.api.JsonApiResources} (resources, create/update authoring), {@link
 * com.kazforge.jsonapi.jackson.api.JsonApiRelationships} (relationship-linkage documents), {@link
 * com.kazforge.jsonapi.jackson.api.JsonApiDocuments} (raw/general documents), and {@link
 * com.kazforge.jsonapi.jackson.api.JsonApiPatches} (presence-aware PATCH). Values {@link
 * com.kazforge.jsonapi.jackson.api.ResourceWriteOptions}, {@link
 * com.kazforge.jsonapi.jackson.api.ResourceDocument}, and {@link
 * com.kazforge.jsonapi.jackson.api.ResourceCollectionDocument} compose existing neutral semantics
 * and add no Jackson mechanics.
 *
 * <p>No type in this package imports or models Jackson implementation types; Jackson 2 and Jackson
 * 3 remain separately compiled implementations of these contracts.
 */
@NullMarked
package com.kazforge.jsonapi.jackson.api;

import org.jspecify.annotations.NullMarked;
