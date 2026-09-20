/**
 * Jackson 3 implementation of the major-neutral Level-1 JSON:API application contract and its
 * advanced document, mapping, typed-envelope, and PATCH capabilities.
 *
 * <p>Ordinary applications obtain an immutable, thread-safe {@link Jackson3JsonApi} from {@link
 * JsonApiJackson3#jsonApi(tools.jackson.databind.json.JsonMapper)} or configure
 * application-lifetime identifier conversion, {@link
 * com.kazforge.jsonapi.jackson3.mapping.RelationshipLinkageMapper linkage mapping}, representation
 * policy, decoration, and a resource-write {@code jsonapi.version} default through {@link
 * JsonApiJackson3#builder}. Representation selection, document envelopes, and expected update
 * identity remain per-operation values. {@link JsonApiJackson3} also exposes the lower-level
 * readers, writers, mappers, binders, typed-envelope readers, and PATCH readers for advanced
 * control.
 *
 * <p>Every entry point starts from a caller-configured {@link
 * tools.jackson.databind.json.JsonMapper}; construction never mutates it. Token-driven document
 * reading uses the supplied mapper, while capabilities that need codec modules or isolated mapping
 * state derive their own mapper. Configured Jackson remains authoritative for property discovery,
 * external names, mix-ins, serializers, deserializers, and value conversion; JSON:API annotations
 * assign semantic roles.
 *
 * <p>Readers decode and aggregate-validate before returning, and writers validate before output.
 * Java {@code null} on model components means member absence; explicit JSON {@code null} uses model
 * variants such as {@link com.kazforge.jsonapi.core.model.DocumentData.NullData}. Read failures use
 * {@link com.kazforge.jsonapi.diagnostic.JsonApiDocumentReadException}, mapping and binding
 * failures use {@link com.kazforge.jsonapi.diagnostic.JsonApiMappingException}, and write
 * validation failures use {@link com.kazforge.jsonapi.core.validation.JsonApiValidationException}.
 * Advanced and Level-1 APIs follow Jackson 3's unchecked I/O and emission model.
 *
 * <p>{@link JsonApiResourceMapper} maps domain values to core documents. Its sparse-fieldset
 * overloads return {@link com.kazforge.jsonapi.mapping.MappedDocument}; {@link
 * JsonApiDocumentWriter} composes that mapping provenance into validation. {@link
 * JsonApiDomainDocumentReader} uses an explicit {@link
 * com.kazforge.jsonapi.mapping.ResourceTypeRegistry} to bind primary and included resources
 * independently into {@link JsonApiDomainDocument}; identifier data remains in the core model and
 * error documents are not bound.
 *
 * <p>{@link JsonApiPatchCommandReader} and {@link JsonApiPatchDtoReader} enforce update-request
 * validation and preserve supplied-member presence, either as a neutral patch command or an
 * application-owned {@link com.kazforge.jsonapi.patch.PatchPresence} DTO. Shared policy,
 * diagnostic, representation, mapping, envelope, and PATCH contracts live in {@link
 * com.kazforge.jsonapi}.
 */
@NullMarked
package com.kazforge.jsonapi.jackson3;

import org.jspecify.annotations.NullMarked;
