/**
 * Backend-independent presence and requested-change contracts for resource PATCH.
 *
 * <p>{@link com.kazforge.jsonapi.patch.PatchPresence} distinguishes an omitted DTO member from a
 * supplied value, including a supplied converted {@code null}. {@link
 * com.kazforge.jsonapi.patch.PatchCommand} contains only supplied mapped changes; omitted
 * attributes, relationships, and meta locations do not appear in its change list.
 *
 * <p>On the low-level path, {@link com.kazforge.jsonapi.patch.StructuredPatch} recursively
 * preserves exactly the supplied members of a traversable structured attribute or meta value.
 * Missing nested members remain omitted, an empty structured patch is a supplied empty object, and
 * list, set, array, map, and relationship values remain whole replacements rather than
 * element-addressed mutation protocols.
 *
 * <p>These values describe a requested change; they do not authorize it or mutate application
 * state. The application owns authorization, concurrency, persistence, and mutation semantics. The
 * current Jackson adapters populate these backend-independent values through caller-configured
 * Jackson conversion.
 */
@NullMarked
package com.kazforge.jsonapi.patch;

import org.jspecify.annotations.NullMarked;
