package com.kazforge.jsonapi.jackson3;

import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.diagnostic.JsonApiDocumentReadException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.document.DocumentReadContext;
import com.kazforge.jsonapi.jackson3.internal.DomainResourceBinder;
import com.kazforge.jsonapi.jackson3.internal.MappingDefinitionCache;
import com.kazforge.jsonapi.jackson3.mapping.RelationshipLinkageMapper;
import com.kazforge.jsonapi.mapping.IdentifierConverter;
import com.kazforge.jsonapi.mapping.ResourceTypeRegistry;
import com.kazforge.jsonapi.mapping.internal.TypedEnvelopeBinder;
import java.io.InputStream;
import java.util.Map;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads validated JSON:API documents into an immutable {@link JsonApiDomainDocument} with flat
 * primary DTOs and independently bound included DTOs.
 *
 * <p>Decoding and aggregate validation run exactly as in {@link JsonApiDocumentReader}: {@link
 * #readValue} overloads share its close/ownership rules and keep codec/validation failures as
 * {@link JsonApiDocumentReadException} with the same category, pointer, location, and rule code.
 * {@link #fromDocument(JsonApiDocument)} binds only and never re-parses or re-validates.
 *
 * <p>Primary resource data and every present {@code included} element are bound through the flat
 * binder after looking up {@link com.kazforge.jsonapi.core.model.ResourceObject#type()} in the
 * supplied {@link ResourceTypeRegistry}; identifier primary data and error documents never attempt
 * DTO binding. Resource types absent from the registry fail with {@link
 * MappingDiagnostic#UNREGISTERED_RESOURCE_TYPE} at the document pointer before any envelope
 * escapes; other binder failures compose structurally with the document pointer ({@code /data},
 * {@code /data/<index>}, {@code /included/<index>}) per the mapping-location contract: a
 * resource-relative binder location joins under the prefix, and a locationless binder failure
 * reports just the prefix. Relationship properties stay linkage-only and {@code included} is never
 * injected.
 *
 * <p>Construct instances via {@link JsonApiJackson3#domainDocumentReader(JsonMapper,
 * DocumentReadContext, ResourceTypeRegistry)} or its overloads, never directly. Construction
 * re-resolves every registered target against the reader's configured resource metadata and rejects
 * keys that disagree with {@link MappingDiagnostic#RESOURCE_TYPE_MISMATCH} without a document
 * location; missing or invalid consumer metadata reports the resolver diagnostic. The reader is
 * safe for concurrent use once created.
 */
public final class JsonApiDomainDocumentReader {

  private final JsonApiDocumentReader documentReader;
  private final TypedEnvelopeBinder<JavaType> envelopeBinder;
  private final JsonApiDomainDocument.MetaConverter metaConverter;

  JsonApiDomainDocumentReader(
      JsonMapper base,
      DocumentReadContext context,
      ResourceTypeRegistry registry,
      IdentifierConverter identifierConverter,
      Map<Class<?>, RelationshipLinkageMapper> linkageMappers) {
    this.documentReader = new JsonApiDocumentReader(base, context);
    JsonMapper binderMapper = JsonApiJackson3Assembly.resourceMappingMapper(base);
    MappingDefinitionCache metadataAuthority = new MappingDefinitionCache(binderMapper);
    DomainResourceBinder binder =
        new DomainResourceBinder(
            binderMapper, identifierConverter, metadataAuthority, linkageMappers);
    this.envelopeBinder =
        new TypedEnvelopeBinder<>(
            registry,
            TypedEnvelopeBinder.backend(
                binderMapper::constructType,
                JavaType::getRawClass,
                metadataAuthority::requireResourceTypeName,
                binder::fromResource));
    this.metaConverter = new BinderMetaConverter(binderMapper);
  }

  /** Decodes, validates, and binds the JSON:API document in the given string. */
  public JsonApiDomainDocument readValue(String json) {
    return fromDocument(documentReader.readValue(json));
  }

  /** Decodes, validates, and binds the UTF-8 JSON:API document in the given bytes. */
  public JsonApiDomainDocument readValue(byte[] utf8Json) {
    return fromDocument(documentReader.readValue(utf8Json));
  }

  /**
   * Decodes, validates, and binds one JSON:API document from a caller-owned UTF-8 stream. The
   * stream is not closed; only the parser created for this call is closed.
   */
  public JsonApiDomainDocument readValue(InputStream utf8Stream) {
    return fromDocument(documentReader.readValue(utf8Stream));
  }

  /**
   * Decodes, validates, and binds one JSON:API document from a caller-owned parser starting at the
   * current token (or the next token if none is current). The parser is not closed.
   */
  public JsonApiDomainDocument readValue(JsonParser parser) {
    return fromDocument(documentReader.readValue(parser));
  }

  /**
   * Binds an already-validated document into a domain envelope; never re-parses or re-validates.
   */
  public JsonApiDomainDocument fromDocument(JsonApiDocument document) {
    return new JsonApiDomainDocument(envelopeBinder.bind(document), metaConverter);
  }
}
