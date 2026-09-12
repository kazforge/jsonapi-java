/**
 * Shared implementation helpers for the core document model and validation.
 *
 * <p>This package is not a public API. It hosts URI/media-type/link/JSON Pointer syntax validators,
 * ordered null-preserving collection copies, and additional-member copy helpers used by {@code
 * core.model} and {@code core.validation}. Inbound JSON Pointer syntax lives in {@link
 * com.kazforge.jsonapi.core.internal.SyntaxValidators}; {@link
 * com.kazforge.jsonapi.core.internal.JsonPointers} is emit/escape only. See ADR-009 for nullness
 * policy.
 */
@NullMarked
package com.kazforge.jsonapi.core.internal;

import org.jspecify.annotations.NullMarked;
