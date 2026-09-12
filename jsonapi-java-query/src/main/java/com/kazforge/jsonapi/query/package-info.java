/**
 * Framework- and Jackson-major-neutral parsing of standardized JSON:API query selection.
 *
 * <p>The parser decodes include paths, sparse fieldsets, and sort fields into neutral values while
 * preserving page, filter, and unknown parameters as ordered opaque data. Parsing does not execute
 * filtering, pagination, persistence projections, authorization, or endpoint policy.
 */
@NullMarked
package com.kazforge.jsonapi.query;

import org.jspecify.annotations.NullMarked;
