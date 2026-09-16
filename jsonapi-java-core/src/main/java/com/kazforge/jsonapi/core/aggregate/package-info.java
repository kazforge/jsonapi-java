/**
 * Aggregate JSON:API document validation over completed core model values.
 *
 * <p>{@link com.kazforge.jsonapi.core.aggregate.JsonApiDocumentValidator} applies rules that need
 * full document context according to a {@link
 * com.kazforge.jsonapi.core.aggregate.ValidationContext}, including resource identity uniqueness,
 * full linkage, operation and endpoint-role policy, link-member context, pagination cardinality,
 * and extension/profile policy. Local construction invariants and stable diagnostics remain owned
 * by lower core packages.
 *
 * <p>See ADR-003, ADR-008, ADR-011, and {@code docs/conformance.md}.
 */
@NullMarked
package com.kazforge.jsonapi.core.aggregate;

import org.jspecify.annotations.NullMarked;
