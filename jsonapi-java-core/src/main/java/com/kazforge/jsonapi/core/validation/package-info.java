/**
 * Aggregate JSON:API document validation, stable diagnostics, and member-name grammar checks.
 *
 * <p>Local invariants are enforced when model values are constructed. {@link
 * com.kazforge.jsonapi.core.validation.MemberNames} validates JSON:API v1.1 member-name grammar.
 * {@link com.kazforge.jsonapi.core.validation.JsonApiDocumentValidator} and {@link
 * com.kazforge.jsonapi.core.validation.ValidationContext} validate rules that need full document
 * context (resource identity uniqueness, full linkage, local-identifier consistency, link-member
 * context, and extension/profile policy). Resource identity uniqueness is representation-strict and
 * alias-aware for identifier collections after id↔lid binding.
 *
 * <p>Operation ({@link com.kazforge.jsonapi.core.validation.DocumentUsage}), endpoint role ({@link
 * com.kazforge.jsonapi.core.validation.PrimaryDataContext}), resource occurrence (primary data
 * versus relationship linkage versus included resources), cardinality (the sealed {@code
 * DocumentData} variant), and link location ({@link
 * com.kazforge.jsonapi.core.validation.LinksContext}) are separate axes. {@link
 * com.kazforge.jsonapi.core.validation.DocumentUsage#UPDATE_REQUEST} on an ordinary resource
 * endpoint additionally requires single-resource primary data, replacement {@code data} on every
 * relationship supplied by the primary resource, and — when an {@link
 * com.kazforge.jsonapi.core.validation.EndpointIdentity} is configured — a primary resource
 * identity matching the expected endpoint. {@link
 * com.kazforge.jsonapi.core.validation.DocumentUsage#CREATE_REQUEST} on an ordinary resource
 * endpoint requires single-resource primary data and requires {@code data} on every relationship
 * supplied by the primary resource while accepting null, single, and collection linkage. Included
 * resources and relationship linkage are exempt from the primary relationship-data rule under both
 * write usages; otherwise existing identity and aggregate rules apply unchanged. Create identity
 * leniency (an omittable resource {@code id} on the primary create resource, with {@code id} and
 * {@code lid} kept independent) applies only to that primary resource occurrence; a {@code
 * lid}-only relationship identifier hosted by the primary resource is accepted only as a
 * self-reference to the same primary resource, while unrelated linkage, linkage hosted by included
 * resources, and included resources themselves require {@code id}. The relationship endpoint role
 * accepts linkage primary data and rejects resource objects with {@code
 * PRIMARY_DATA_CONTEXT_MISMATCH}. A top-level {@code related} link is accepted only on the
 * relationship endpoint role; ordinary resource responses, including related-resource fetches,
 * reject it with {@code INVALID_LINKS_CONTEXT}, and allowed profile member names do not override
 * this restriction.
 *
 * <p>Failures carry a stable {@link com.kazforge.jsonapi.core.validation.ValidationRuleCode} and a
 * JSON Pointer-like path. See ADR-003, ADR-009, ADR-012, and {@code docs/conformance.md}.
 */
@NullMarked
package com.kazforge.jsonapi.core.validation;

import org.jspecify.annotations.NullMarked;
