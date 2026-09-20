/**
 * Backend-independent JSON:API document read/write contract values.
 *
 * <p>{@link com.kazforge.jsonapi.document.DocumentReadContext} keeps aggregate-validation policy
 * separate from {@link com.kazforge.jsonapi.document.PrimaryDataKind}. The validation context
 * selects operation and endpoint semantics; the primary-data kind independently selects whether
 * primary objects and arrays decode as resources or identifiers. Readers do not derive one axis
 * from the other.
 *
 * <p>{@link com.kazforge.jsonapi.document.DocumentEnvelope} carries optional top-level write
 * members. A {@code null} component means absence, while a non-null empty value remains a present
 * empty object on the wire. These values carry document state only; the current Jackson adapters
 * supply the decode/encode mechanics through caller-configured Jackson.
 */
@NullMarked
package com.kazforge.jsonapi.document;

import org.jspecify.annotations.NullMarked;
