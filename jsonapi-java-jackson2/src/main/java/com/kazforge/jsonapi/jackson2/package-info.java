/**
 * Jackson 2 implementation of the major-neutral Level-1 JSON:API application contract and its
 * advanced document, mapping, typed-envelope, and PATCH capabilities.
 *
 * <p>Ordinary applications obtain an immutable, thread-safe {@link Jackson2JsonApi} from {@link
 * JsonApiJackson2#jsonApi(com.fasterxml.jackson.databind.json.JsonMapper)}, or from {@link
 * JsonApiJackson2#builder} to configure application-lifetime identifier conversion, {@link
 * com.kazforge.jsonapi.jackson2.mapping.RelationshipLinkageMapper linkage mapping}, representation
 * policy, decoration, or a resource-write {@code jsonapi.version} default. {@link JsonApiJackson2}
 * also creates the lower-level readers, writers, mappers, binders, typed-envelope readers, and
 * PATCH readers for advanced control.
 *
 * <p>Every entry point starts from a caller-configured {@link
 * com.fasterxml.jackson.databind.json.JsonMapper} and never mutates it. Configured Jackson remains
 * authoritative for property discovery, external names, and value conversion; JSON:API annotations
 * assign semantic roles. Advanced reader and writer APIs retain Jackson 2's checked {@link
 * java.io.IOException} model; Level-1 operations adapt unavoidable checked stream failures to
 * {@link java.io.UncheckedIOException}.
 *
 * <p>Shared document, mapping, representation, PATCH, and diagnostic contracts live in {@link
 * com.kazforge.jsonapi}.
 */
@NullMarked
package com.kazforge.jsonapi.jackson2;

import org.jspecify.annotations.NullMarked;
