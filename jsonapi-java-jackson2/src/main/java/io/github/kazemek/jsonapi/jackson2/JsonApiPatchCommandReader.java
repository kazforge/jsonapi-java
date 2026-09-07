package io.github.kazemek.jsonapi.jackson2;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.github.kazemek.jsonapi.core.model.DocumentData;
import io.github.kazemek.jsonapi.core.model.JsonApiDocument;
import io.github.kazemek.jsonapi.core.model.ResourceObject;
import io.github.kazemek.jsonapi.core.validation.DocumentUsage;
import io.github.kazemek.jsonapi.core.validation.ValidationContext;
import io.github.kazemek.jsonapi.jackson.document.DocumentReadContext;
import io.github.kazemek.jsonapi.jackson.document.PrimaryDataKind;
import io.github.kazemek.jsonapi.jackson.mapping.IdentifierConverter;
import io.github.kazemek.jsonapi.jackson.patch.PatchCommand;
import io.github.kazemek.jsonapi.jackson2.internal.DomainPatchBinder;
import io.github.kazemek.jsonapi.jackson2.internal.MappingDefinitionCache;
import io.github.kazemek.jsonapi.jackson2.internal.MetaBindingModule;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads a validated JSON:API resource-update document into an immutable {@link PatchCommand}.
 *
 * <p>{@link #readValue} decodes and aggregate-validates through a factory-composed {@link
 * DocumentReadContext} ({@link PrimaryDataKind#RESOURCE} with {@link
 * DocumentUsage#UPDATE_REQUEST}), then binds only supplied mapped attributes and relationships.
 * {@link #fromDocument} binds without re-validation. Codec and aggregate failures stay {@link
 * io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiDocumentReadException}; bind failures stay
 * {@link io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException} with
 * resource-relative pointers and are never prefixed with {@code /data}. Built-in linkage conversion
 * preserves {@code ResourceIdentifier.meta} through a binder mapper that can round-trip core {@link
 * io.github.kazemek.jsonapi.core.model.Meta}.
 *
 * <p>Close/ownership rules match {@link JsonApiDocumentReader}: convenience overloads close parsers
 * they create; caller-owned streams and parsers stay open. Construct via {@link
 * JsonApiJackson2#patchCommandReader(JsonMapper)} or its overloads. Safe for concurrent use once
 * created.
 */
public final class JsonApiPatchCommandReader {

  private static final String RESOURCE_TYPE = "resourceType";

  private final JsonApiDocumentReader documentReader;
  private final JsonMapper binderMapper;
  private final DomainPatchBinder binder;

  JsonApiPatchCommandReader(
      JsonMapper base,
      ValidationContext validationContext,
      IdentifierConverter identifierConverter,
      Map<Class<?>, RelationshipLinkageMapper> linkageMappers) {
    ValidationContext forced =
        Objects.requireNonNull(validationContext, "validationContext")
            .withDocumentUsage(DocumentUsage.UPDATE_REQUEST);
    DocumentReadContext readContext = DocumentReadContext.of(forced, PrimaryDataKind.RESOURCE);
    this.documentReader = new JsonApiDocumentReader(base, readContext);
    this.binderMapper = patchBinderMapper(base);
    this.binder =
        new DomainPatchBinder(
            binderMapper,
            identifierConverter,
            new MappingDefinitionCache(binderMapper),
            linkageMappers);
  }

  private static JsonMapper patchBinderMapper(JsonMapper base) {
    JsonMapper.Builder derived = base.rebuild().addModule(new MetaBindingModule());
    if (!supportsOptionalDeserialization(base)) {
      derived = derived.addModule(new Jdk8Module());
    }
    return derived.build();
  }

  private static boolean supportsOptionalDeserialization(JsonMapper mapper) {
    JavaType type = mapper.constructType(new TypeReference<Optional<String>>() {});
    try (TokenBuffer buffer = new TokenBuffer(mapper, false)) {
      buffer.writeString("probe");
      try (JsonParser parser = buffer.asParser(mapper)) {
        mapper.readValue(parser, type);
        return true;
      }
    } catch (RuntimeException | IOException e) {
      return false;
    }
  }

  /** Decodes, validates, and binds the JSON:API update document in the given string. */
  public <T> PatchCommand<T> readValue(String json, Class<T> resourceType) throws IOException {
    Objects.requireNonNull(resourceType, RESOURCE_TYPE);
    return fromDocument(documentReader.readValue(json), resourceType);
  }

  /** Decodes, validates, and binds the UTF-8 JSON:API update document in the given bytes. */
  public <T> PatchCommand<T> readValue(byte[] utf8Json, Class<T> resourceType) throws IOException {
    Objects.requireNonNull(resourceType, RESOURCE_TYPE);
    return fromDocument(documentReader.readValue(utf8Json), resourceType);
  }

  /**
   * Decodes, validates, and binds one JSON:API update document from a caller-owned UTF-8 stream.
   * The stream is not closed.
   */
  public <T> PatchCommand<T> readValue(InputStream utf8Stream, Class<T> resourceType)
      throws IOException {
    Objects.requireNonNull(resourceType, RESOURCE_TYPE);
    return fromDocument(documentReader.readValue(utf8Stream), resourceType);
  }

  /**
   * Decodes, validates, and binds one JSON:API update document from a caller-owned parser. The
   * parser is not closed.
   */
  public <T> PatchCommand<T> readValue(JsonParser parser, Class<T> resourceType)
      throws IOException {
    Objects.requireNonNull(resourceType, RESOURCE_TYPE);
    return fromDocument(documentReader.readValue(parser), resourceType);
  }

  /**
   * Decodes, validates, and binds using a {@link JavaType}; {@link PatchCommand#resourceType()} is
   * the type's raw class.
   */
  @SuppressWarnings("java:S1452")
  public PatchCommand<?> readValue(String json, JavaType resourceType) throws IOException {
    Objects.requireNonNull(resourceType, RESOURCE_TYPE);
    return fromDocument(documentReader.readValue(json), resourceType);
  }

  /** Byte-array overload of {@link #readValue(String, JavaType)}. */
  @SuppressWarnings("java:S1452")
  public PatchCommand<?> readValue(byte[] utf8Json, JavaType resourceType) throws IOException {
    Objects.requireNonNull(resourceType, RESOURCE_TYPE);
    return fromDocument(documentReader.readValue(utf8Json), resourceType);
  }

  /** Stream overload of {@link #readValue(String, JavaType)}. */
  @SuppressWarnings("java:S1452")
  public PatchCommand<?> readValue(InputStream utf8Stream, JavaType resourceType)
      throws IOException {
    Objects.requireNonNull(resourceType, RESOURCE_TYPE);
    return fromDocument(documentReader.readValue(utf8Stream), resourceType);
  }

  /** Parser overload of {@link #readValue(String, JavaType)}. */
  @SuppressWarnings("java:S1452")
  public PatchCommand<?> readValue(JsonParser parser, JavaType resourceType) throws IOException {
    Objects.requireNonNull(resourceType, RESOURCE_TYPE);
    return fromDocument(documentReader.readValue(parser), resourceType);
  }

  /**
   * Binds an already-validated document without re-parsing or re-validating. Requires non-null
   * {@link DocumentData.SingleResource} primary data; other primary-data states throw {@link
   * IllegalArgumentException}.
   */
  @SuppressWarnings("unchecked")
  public <T> PatchCommand<T> fromDocument(JsonApiDocument document, Class<T> resourceType) {
    Objects.requireNonNull(resourceType, RESOURCE_TYPE);
    return (PatchCommand<T>) fromDocument(document, binderMapper.constructType(resourceType));
  }

  /**
   * Binds an already-validated document without re-parsing or re-validating. Requires non-null
   * {@link DocumentData.SingleResource} primary data; other primary-data states throw {@link
   * IllegalArgumentException}. {@link PatchCommand#resourceType()} is the type's raw class.
   */
  @SuppressWarnings("java:S1452")
  public PatchCommand<?> fromDocument(JsonApiDocument document, JavaType resourceType) {
    Objects.requireNonNull(document, "document");
    Objects.requireNonNull(resourceType, RESOURCE_TYPE);
    DocumentData data = document.data();
    if (!(data instanceof DocumentData.SingleResource(ResourceObject resource))) {
      throw new IllegalArgumentException(
          "fromDocument requires DocumentData.SingleResource primary data");
    }
    return binder.fromResource(resource, resourceType);
  }
}
