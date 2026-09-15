/**
 * JSON:API validation diagnostics, member-name grammar, and model-independent validation policy
 * values.
 *
 * <p>Local model construction and aggregate document validation report failures through {@link
 * com.kazforge.jsonapi.core.validation.JsonApiValidationException} with a stable {@link
 * com.kazforge.jsonapi.core.validation.ValidationRuleCode} and JSON Pointer-like path. {@link
 * com.kazforge.jsonapi.core.validation.MemberNames} validates JSON:API v1.1 member-name grammar.
 * Operation, endpoint role, link location, endpoint identity, and relationship pagination values
 * are shared inputs to aggregate validation without depending on the model or aggregate layer.
 *
 * <p>See ADR-003, ADR-009, ADR-012, and {@code docs/conformance.md}.
 */
@NullMarked
package com.kazforge.jsonapi.core.validation;

import org.jspecify.annotations.NullMarked;
