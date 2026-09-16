package com.kazforge.jsonapi.jackson.api;

/**
 * Major-neutral Level-1 JSON:API application entry point.
 *
 * <p>Level 1 coordinates ordinary client and server operations: strict resource reads,
 * single/collection writes, create/update authoring, linkage documents, raw documents with explicit
 * context, and presence-aware PATCH. Major-specific advanced APIs remain available for Jackson type
 * models, heterogeneous envelopes, runtime construction, and direct codec, mapper, or binder
 * control.
 *
 * <p>The contract is bidirectional and does not encode HTTP transport, persistence, authorization,
 * or controller policy. Application-lifetime Jackson and representation configuration belongs to
 * the major-specific runtime; this interface exposes cohesive per-operation facets only.
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
