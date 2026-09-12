/**
 * Representation shaping and inclusion/fieldset contracts.
 *
 * <p>Provides {@link com.kazforge.jsonapi.jackson.representation.IncludePath}, {@link
 * com.kazforge.jsonapi.jackson.representation.IncludePolicy}, {@link
 * com.kazforge.jsonapi.jackson.representation.FieldPolicy}, {@link
 * com.kazforge.jsonapi.jackson.representation.RepresentationSelection}, and {@link
 * com.kazforge.jsonapi.jackson.representation.RepresentationPolicy}. A {@link
 * com.kazforge.jsonapi.jackson.representation.RepresentationSelection} is per operation, requests
 * only include paths and sparse fieldsets, and preserves whether {@code include} was explicitly
 * supplied. A {@link com.kazforge.jsonapi.jackson.representation.RepresentationPolicy} is
 * application-scoped and permits and bounds those requests; it is not a complete authorization
 * system.
 */
@NullMarked
package com.kazforge.jsonapi.jackson.representation;

import org.jspecify.annotations.NullMarked;
