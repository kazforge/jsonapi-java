/**
 * Aggregate JSON:API document validation, stable diagnostics, and member-name grammar checks.
 *
 * <p>Local invariants are enforced when model values are constructed. {@link
 * com.kazforge.jsonapi.core.validation.MemberNames} validates JSON:API v1.1 member-name grammar.
 * {@link com.kazforge.jsonapi.core.validation.JsonApiDocumentValidator} and {@link
 * com.kazforge.jsonapi.core.validation.ValidationContext} validate rules that need full document
 * context (resource identity uniqueness, full linkage, local-identifier consistency, link-member
 * context, and extension/profile policy). Resource identity uniqueness is representation-strict and
 * alias-aware for identifier collections after id↔lid binding. {@link
 * com.kazforge.jsonapi.core.validation.DocumentUsage#UPDATE_REQUEST} additionally requires
 * single-resource primary data, replacement {@code data} on every relationship supplied by the
 * primary resource, and — when an {@link com.kazforge.jsonapi.core.validation.EndpointIdentity} is
 * configured — a primary resource identity matching the expected endpoint. {@link
 * com.kazforge.jsonapi.core.validation.DocumentUsage#CREATE_REQUEST} requires single-resource
 * primary data, permits an omitted resource {@code id} with {@code id} and {@code lid} kept
 * independent, and requires {@code data} on every relationship supplied by the primary resource
 * while accepting null, single, and collection linkage. Included resources are exempt from the
 * primary-resource relationship-data rule under both write usages; otherwise existing identity and
 * aggregate rules apply unchanged.
 *
 * <p>Failures carry a stable {@link com.kazforge.jsonapi.core.validation.ValidationRuleCode} and a
 * JSON Pointer-like path. See ADR-003, ADR-009, ADR-012, and {@code docs/conformance.md}.
 */
@NullMarked
package com.kazforge.jsonapi.core.validation;

import org.jspecify.annotations.NullMarked;
