/**
 * Backend-independent Level-1 JSON:API application operations.
 *
 * <p>Level 1 coordinates the ordinary client/server-neutral operations that applications commonly
 * need. Major-specific advanced APIs remain the explicit control surface for mapper configuration,
 * Jackson-native type models, heterogeneous registry-backed envelopes, and direct codec, mapping,
 * or binding composition. The Level-1 facade never guesses an ambiguous document shape or resource
 * target.
 *
 * <p>The root {@link com.kazforge.jsonapi.jackson.api.JsonApi} exposes four facets: {@link
 * com.kazforge.jsonapi.jackson.api.JsonApiResources} (resources, create/update authoring), {@link
 * com.kazforge.jsonapi.jackson.api.JsonApiRelationships} (relationship-linkage documents), {@link
 * com.kazforge.jsonapi.jackson.api.JsonApiDocuments} (raw/general documents), and {@link
 * com.kazforge.jsonapi.jackson.api.JsonApiPatches} (presence-aware PATCH). Values {@link
 * com.kazforge.jsonapi.jackson.api.ResourceWriteOptions}, {@link
 * com.kazforge.jsonapi.jackson.api.ResourceDocument}, and {@link
 * com.kazforge.jsonapi.jackson.api.ResourceCollectionDocument} carry neutral per-operation state
 * and add no Jackson mechanics.
 *
 * <p>No type in this package imports or models backend implementation types. The current Jackson 2
 * and Jackson 3 runtimes implement these contracts through caller-configured Jackson, which remains
 * the current property authority for discovery, visibility, external names, construction, and
 * conversion. The facade adds no unified failure family: document-read, core validation, and
 * mapping failures retain their focused contracts, and each adapter documents how its transport I/O
 * failures cross the Level-1 boundary.
 */
@NullMarked
package com.kazforge.jsonapi.jackson.api;

import org.jspecify.annotations.NullMarked;
