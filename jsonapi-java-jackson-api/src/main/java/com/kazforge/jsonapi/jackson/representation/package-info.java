/**
 * Backend-independent representation shaping and inclusion/fieldset contracts.
 *
 * <p>A {@link com.kazforge.jsonapi.jackson.representation.RepresentationSelection} is per
 * operation, requests only include paths and sparse fieldsets, and preserves whether {@code
 * include} was explicitly supplied. A {@link
 * com.kazforge.jsonapi.jackson.representation.RepresentationPolicy} is application/runtime
 * configuration that permits and bounds those requests. A selection does not grant permission or
 * override policy, and representation policy is not a substitute for endpoint, resource, or field
 * authorization. Selection and policy carry JSON:API member names; the current Jackson adapters
 * resolve them through caller-configured Jackson external names.
 */
@NullMarked
package com.kazforge.jsonapi.jackson.representation;

import org.jspecify.annotations.NullMarked;
