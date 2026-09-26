/**
 * Jackson 3 implementation of the major-neutral Level-1 JSON:API application contract and its
 * advanced document, mapping, typed-envelope, and PATCH capabilities.
 *
 * <p>Ordinary applications obtain an immutable, thread-safe {@link Jackson3JsonApi} from {@link
 * JsonApiJackson3#jsonApi(tools.jackson.databind.json.JsonMapper)}, or from {@link
 * JsonApiJackson3#builder} to configure application-lifetime identifier conversion, {@link
 * com.kazforge.jsonapi.jackson3.mapping.RelationshipLinkageMapper linkage mapping}, representation
 * policy, decoration, or a resource-write {@code jsonapi.version} default. {@link JsonApiJackson3}
 * also creates the lower-level readers, writers, mappers, binders, typed-envelope readers, and
 * PATCH readers for advanced control.
 *
 * <p>Every entry point starts from a caller-configured {@link
 * tools.jackson.databind.json.JsonMapper} and never mutates it. Configured Jackson remains
 * authoritative for property discovery, external names, and value conversion; JSON:API annotations
 * assign semantic roles. Advanced and Level-1 APIs follow Jackson 3's unchecked I/O and emission
 * model.
 *
 * <p>Shared document, mapping, representation, PATCH, and diagnostic contracts live in {@link
 * com.kazforge.jsonapi}.
 */
@NullMarked
package com.kazforge.jsonapi.jackson3;

import org.jspecify.annotations.NullMarked;
