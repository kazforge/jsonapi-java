/**
 * Representation shaping and inclusion/fieldset contracts.
 *
 * <p>Provides {@link io.github.kazemek.jsonapi.jackson.representation.IncludePath}, {@link
 * io.github.kazemek.jsonapi.jackson.representation.IncludePolicy}, {@link
 * io.github.kazemek.jsonapi.jackson.representation.FieldPolicy}, {@link
 * io.github.kazemek.jsonapi.jackson.representation.RepresentationSelection}, and {@link
 * io.github.kazemek.jsonapi.jackson.representation.RepresentationPolicy}. A {@link
 * io.github.kazemek.jsonapi.jackson.representation.RepresentationSelection} is per operation,
 * requests only include paths and sparse fieldsets, and preserves whether {@code include} was
 * explicitly supplied. A {@link
 * io.github.kazemek.jsonapi.jackson.representation.RepresentationPolicy} is application-scoped and
 * permits and bounds those requests; it is not a complete authorization system.
 */
@NullMarked
package io.github.kazemek.jsonapi.jackson.representation;

import org.jspecify.annotations.NullMarked;
