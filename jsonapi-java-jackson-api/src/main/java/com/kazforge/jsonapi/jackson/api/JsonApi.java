package com.kazforge.jsonapi.jackson.api;

/**
 * Major-neutral Level-1 JSON:API application entry point.
 *
 * <p>Level 1 expresses JSON:API application semantics for ordinary client and server code: strict
 * resource reads, single/collection writes, create/update authoring, linkage documents, raw
 * documents with explicit context, and presence-aware PATCH. It sits above — and never replaces —
 * the advanced capability APIs (document readers/writers, resource mapper/binder, {@code JavaType}
 * overloads, heterogeneous envelopes, low-level contexts), which remain the explicit
 * mechanism/control seams in the major-specific adapters.
 *
 * <p>The contract is client/server-neutral and bidirectional: no operation encodes HTTP transport,
 * controller semantics, or server-only policy. It models no Jackson mechanics: no mapper, type,
 * parser/generator, serializer/deserializer, introspector, or runtime major detection.
 * Application-lifetime configuration lives in major-specific runtimes; this interface exposes
 * cohesive per-operation facets only.
 */
public interface JsonApi {

  /** Ordinary resource reads, writes, and create/update authoring. */
  JsonApiResources resources();

  /** Relationship-linkage document reads and writes. */
  JsonApiRelationships relationships();

  /** Raw/general document operations with explicit semantic context. */
  JsonApiDocuments documents();

  /** Presence-aware PATCH binding: typed DTOs and low-level commands. */
  JsonApiPatches patches();
}
