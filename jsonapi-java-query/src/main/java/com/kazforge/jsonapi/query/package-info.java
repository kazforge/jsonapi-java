/**
 * Framework- and Jackson-major-neutral parsing of standardized JSON:API query selection.
 *
 * <p>The parser recognizes {@code include}, {@code fields[TYPE]}, and {@code sort} without
 * trimming, renaming, or resolving their JSON:API tokens. It preserves page, filter, and unknown
 * parameters as ordered opaque data. Parsing and optional exact allow-list checks produce request
 * selection only; they do not execute queries or decide representation, authorization, persistence,
 * transport, or endpoint policy.
 */
@NullMarked
package com.kazforge.jsonapi.query;

import org.jspecify.annotations.NullMarked;
