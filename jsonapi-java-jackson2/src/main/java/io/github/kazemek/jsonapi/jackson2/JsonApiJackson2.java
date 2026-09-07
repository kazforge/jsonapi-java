package io.github.kazemek.jsonapi.jackson2;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.ser.DefaultSerializerProvider;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.github.kazemek.jsonapi.core.validation.ValidationContext;
import io.github.kazemek.jsonapi.jackson.document.DocumentReadContext;
import io.github.kazemek.jsonapi.jackson.mapping.IdentifierConverter;
import io.github.kazemek.jsonapi.jackson.mapping.ResourceDecoratorRegistry;
import io.github.kazemek.jsonapi.jackson2.internal.DomainResourceBinder;
import io.github.kazemek.jsonapi.jackson2.internal.DomainResourceWriter;
import io.github.kazemek.jsonapi.jackson2.internal.JsonApiDocumentModule;
import io.github.kazemek.jsonapi.jackson2.internal.MappingDefinitionCache;
import io.github.kazemek.jsonapi.jackson2.internal.MetaBindingModule;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Factory for the Jackson 2 JSON:API document writer, validated document reader, resource mapper,
 * flat DTO resource binder, and presence-aware PATCH readers.
 *
 * <p>Callers supply an already-configured {@link JsonMapper}. Each canonical factory accepts that
 * mapper first, followed by the capability-specific context and collaborators; the writer
 * convenience factory selects documented defaults and delegates. Mapper builders are intentionally
 * not accepted, and factory construction never mutates or replaces the caller's configuration in
 * place. The writer derives a codec-configured mapper via {@link JsonMapper#rebuild()} and
 * registers only the internal JSON:API document module; the reader uses the supplied mapper
 * directly for token-driven parsing; the resource mapper derives an isolated mapping mapper via
 * {@link JsonMapper#rebuild()} with only mapping-required internal module support, including a
 * caller-preserving JDK 8 {@code Optional} fallback. Public surface consists of {@link
 * JsonApiDocumentWriter}, {@link JsonApiDocumentReader}, {@link JsonApiResourceMapper}, {@link
 * JsonApiResourceBinder}, {@link JsonApiPatchCommandReader}, and {@link JsonApiPatchDtoReader};
 * additional capabilities follow in later parity stories per ADR-016's semantic cross-major policy.
 */
public final class JsonApiJackson2 {

  private static final String CONTEXT = "context";
  private static final String IDENTIFIER_CONVERTER = "identifierConverter";
  private static final String LINKAGE_MAPPERS = "linkageMappers";

  private JsonApiJackson2() {}

  /**
   * Returns a writer that validates with {@link ValidationContext#defaults()} then serializes
   * documents using a derived codec-configured mapper.
   */
  public static JsonApiDocumentWriter writer(JsonMapper base) {
    return writer(base, ValidationContext.defaults());
  }

  /**
   * Returns a writer that validates with the given context then serializes documents using a
   * derived codec-configured mapper.
   */
  public static JsonApiDocumentWriter writer(JsonMapper base, ValidationContext context) {
    Objects.requireNonNull(base, "base");
    Objects.requireNonNull(context, CONTEXT);
    return new JsonApiDocumentWriter(documentMapper(base), context);
  }

  /**
   * Returns a reader bound to the given read context. Decoding is token-driven and does not use
   * document serializers, so the caller mapper is used as-is.
   */
  public static JsonApiDocumentReader reader(JsonMapper base, DocumentReadContext context) {
    Objects.requireNonNull(base, "base");
    Objects.requireNonNull(context, CONTEXT);
    return new JsonApiDocumentReader(base, context);
  }

  /**
   * Returns a resource mapper with default identifier conversion. Derives a new mapper via {@link
   * JsonMapper#rebuild()} and never mutates the caller's mapper.
   */
  public static JsonApiResourceMapper resourceMapper(JsonMapper base) {
    return resourceMapper(base, IdentifierConverter.defaults());
  }

  /**
   * Returns a resource mapper with the given identifier converter. Derives a new mapper via {@link
   * JsonMapper#rebuild()} and never mutates the caller's mapper.
   */
  public static JsonApiResourceMapper resourceMapper(
      JsonMapper base, IdentifierConverter identifierConverter) {
    return resourceMapper(base, identifierConverter, ResourceDecoratorRegistry.empty());
  }

  /**
   * Returns a resource mapper with default identifier conversion and the given decoration registry.
   * Derives a new mapper via {@link JsonMapper#rebuild()} and never mutates the caller's mapper.
   *
   * <p>Relationship decoration is keyed by the mapped property <em>identity</em> (Jackson logical
   * name), not the wire name; the mapper follows configured-Jackson renaming automatically.
   */
  public static JsonApiResourceMapper resourceMapper(
      JsonMapper base, ResourceDecoratorRegistry decorators) {
    return resourceMapper(base, IdentifierConverter.defaults(), decorators);
  }

  /**
   * Returns a resource mapper with the given identifier converter and decoration registry. Derives
   * a new mapper via {@link JsonMapper#rebuild()} and never mutates the caller's mapper.
   *
   * <p>Decorators add only {@code ResourceObject.links} and {@code Relationship.links} for existing
   * mapped relationships. They never replace type/id/attributes/linkage/meta/inclusion or resurrect
   * a fieldset-omitted relationship.
   */
  public static JsonApiResourceMapper resourceMapper(
      JsonMapper base,
      IdentifierConverter identifierConverter,
      ResourceDecoratorRegistry decorators) {
    Objects.requireNonNull(base, "base");
    Objects.requireNonNull(identifierConverter, IDENTIFIER_CONVERTER);
    Objects.requireNonNull(decorators, "decorators");
    JsonMapper derived = resourceMappingMapper(base);
    DomainResourceWriter writer =
        new DomainResourceWriter(
            derived, identifierConverter, new MappingDefinitionCache(derived), decorators);
    return new JsonApiResourceMapper(writer);
  }

  /**
   * Returns a flat DTO binder with default identifier conversion and no custom relationship linkage
   * mappers. Derives a new mapper via {@link JsonMapper#rebuild()} and never mutates the caller's
   * mapper.
   */
  public static JsonApiResourceBinder resourceBinder(JsonMapper base) {
    return resourceBinder(base, IdentifierConverter.defaults(), Map.of());
  }

  /**
   * Returns a flat DTO binder with the given identifier converter and no custom relationship
   * linkage mappers. Derives a new mapper via {@link JsonMapper#rebuild()} and never mutates the
   * caller's mapper.
   */
  public static JsonApiResourceBinder resourceBinder(
      JsonMapper base, IdentifierConverter identifierConverter) {
    return resourceBinder(base, identifierConverter, Map.of());
  }

  /**
   * Returns a flat DTO binder with the given identifier converter and relationship linkage mappers
   * keyed by relationship target class. Derives a new mapper via {@link JsonMapper#rebuild()} and
   * never mutates the caller's mapper.
   */
  public static JsonApiResourceBinder resourceBinder(
      JsonMapper base,
      IdentifierConverter identifierConverter,
      Map<Class<?>, RelationshipLinkageMapper> linkageMappers) {
    Objects.requireNonNull(base, "base");
    Objects.requireNonNull(identifierConverter, IDENTIFIER_CONVERTER);
    Objects.requireNonNull(linkageMappers, LINKAGE_MAPPERS);
    JsonMapper derived = resourceBindingMapper(base);
    DomainResourceBinder binder =
        new DomainResourceBinder(
            derived, identifierConverter, new MappingDefinitionCache(derived), linkageMappers);
    return new JsonApiResourceBinder(derived, binder);
  }

  /**
   * Returns a presence-aware PATCH command reader with {@link ValidationContext#defaults()},
   * default identifier conversion, and no custom relationship linkage mappers. Forces {@code
   * DocumentUsage.UPDATE_REQUEST} and {@code PrimaryDataKind.RESOURCE} for validate-on-read.
   */
  public static JsonApiPatchCommandReader patchCommandReader(JsonMapper base) {
    return patchCommandReader(
        base, ValidationContext.defaults(), IdentifierConverter.defaults(), Map.of());
  }

  /**
   * Returns a presence-aware PATCH command reader with the given validation context, default
   * identifier conversion, and no custom relationship linkage mappers. Forces update-request usage
   * while preserving other context fields (including expected endpoint identity).
   */
  public static JsonApiPatchCommandReader patchCommandReader(
      JsonMapper base, ValidationContext validationContext) {
    return patchCommandReader(base, validationContext, IdentifierConverter.defaults(), Map.of());
  }

  /**
   * Returns a presence-aware PATCH command reader with the given validation context and identifier
   * converter, and no custom relationship linkage mappers.
   */
  public static JsonApiPatchCommandReader patchCommandReader(
      JsonMapper base,
      ValidationContext validationContext,
      IdentifierConverter identifierConverter) {
    return patchCommandReader(base, validationContext, identifierConverter, Map.of());
  }

  /**
   * Returns a presence-aware PATCH command reader with the given validation context, identifier
   * converter, and relationship linkage mappers keyed by relationship target class. Snapshots the
   * linkage-mapper map with {@link Map#copyOf}; derives a binder mapper via {@link
   * JsonMapper#rebuild()} and never mutates the caller's mapper.
   */
  public static JsonApiPatchCommandReader patchCommandReader(
      JsonMapper base,
      ValidationContext validationContext,
      IdentifierConverter identifierConverter,
      Map<Class<?>, RelationshipLinkageMapper> linkageMappers) {
    Objects.requireNonNull(base, "base");
    Objects.requireNonNull(validationContext, CONTEXT);
    Objects.requireNonNull(identifierConverter, IDENTIFIER_CONVERTER);
    Objects.requireNonNull(linkageMappers, LINKAGE_MAPPERS);
    return new JsonApiPatchCommandReader(
        base, validationContext, identifierConverter, linkageMappers);
  }

  /**
   * Returns a direct typed PATCH DTO reader with {@link ValidationContext#defaults()}, default
   * identifier conversion, and no custom relationship linkage mappers. Forces {@code
   * DocumentUsage.UPDATE_REQUEST} and {@code PrimaryDataKind.RESOURCE} for validate-on-read.
   * Derives a binder mapper via {@link JsonMapper#rebuild()} plus the internal {@code
   * PatchPresence} module and never mutates the caller's mapper.
   */
  public static JsonApiPatchDtoReader patchDtoReader(JsonMapper base) {
    return patchDtoReader(
        base, ValidationContext.defaults(), IdentifierConverter.defaults(), Map.of());
  }

  /**
   * Returns a direct typed PATCH DTO reader with the given validation context, default identifier
   * conversion, and no custom relationship linkage mappers. Forces update-request usage while
   * preserving other context fields (including expected endpoint identity).
   */
  public static JsonApiPatchDtoReader patchDtoReader(
      JsonMapper base, ValidationContext validationContext) {
    return patchDtoReader(base, validationContext, IdentifierConverter.defaults(), Map.of());
  }

  /**
   * Returns a direct typed PATCH DTO reader with the given validation context and identifier
   * converter, and no custom relationship linkage mappers.
   */
  public static JsonApiPatchDtoReader patchDtoReader(
      JsonMapper base,
      ValidationContext validationContext,
      IdentifierConverter identifierConverter) {
    return patchDtoReader(base, validationContext, identifierConverter, Map.of());
  }

  /**
   * Returns a direct typed PATCH DTO reader with the given validation context, identifier
   * converter, and relationship linkage mappers keyed by relationship target class. Snapshots the
   * linkage-mapper map with {@link Map#copyOf}; derives a binder mapper via {@link
   * JsonMapper#rebuild()} plus the internal {@code PatchPresence} module and never mutates the
   * caller's mapper.
   */
  public static JsonApiPatchDtoReader patchDtoReader(
      JsonMapper base,
      ValidationContext validationContext,
      IdentifierConverter identifierConverter,
      Map<Class<?>, RelationshipLinkageMapper> linkageMappers) {
    Objects.requireNonNull(base, "base");
    Objects.requireNonNull(validationContext, CONTEXT);
    Objects.requireNonNull(identifierConverter, IDENTIFIER_CONVERTER);
    Objects.requireNonNull(linkageMappers, LINKAGE_MAPPERS);
    return new JsonApiPatchDtoReader(base, validationContext, identifierConverter, linkageMappers);
  }

  /**
   * Returns {@code true} when the caller's configured mapper already serializes a present {@code
   * Optional} without the adapter's fallback (JDK 8 datatype module, a custom serializer, or a
   * relaxed bean-serialization configuration). Any failure counts as missing support.
   */
  private static boolean supportsOptionalSerialization(JsonMapper mapper) {
    try (TokenBuffer buffer = new TokenBuffer(mapper, false)) {
      ((DefaultSerializerProvider) mapper.getSerializerProviderInstance())
          .serializeValue(buffer, Optional.of("probe"));
      return true;
    } catch (RuntimeException | IOException e) {
      return false;
    }
  }

  /**
   * Returns {@code true} when the caller's configured mapper already deserializes a present {@code
   * Optional} without the adapter's fallback (JDK 8 datatype module or a custom deserializer). Any
   * failure counts as missing support.
   */
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

  /**
   * Derives a mapper for resource mapping introspection and attribute/meta conversion. Registers
   * {@link MetaBindingModule} so built-in {@code ResourceIdentifier} values can round-trip
   * identifier meta. When the caller's configuration cannot serialize a present {@code Optional},
   * the derived mapping mapper also registers the pinned JDK 8 datatype module so the supported
   * cross-major Optional contract holds on the default configured-mapper path; caller-supplied
   * Optional serialization is detected behaviorally and always wins. Does not register the JSON:API
   * document module because the resource mapper produces core model objects, not serialized output.
   */
  private static JsonMapper resourceMappingMapper(JsonMapper base) {
    JsonMapper.Builder derived = base.rebuild().addModule(new MetaBindingModule());
    if (!supportsOptionalSerialization(base)) {
      derived = derived.addModule(new Jdk8Module());
    }
    return derived.build();
  }

  /**
   * Derives a mapper for resource mapping introspection, identifier conversion, and flat binder
   * construction. Registers {@link MetaBindingModule} so built-in {@code ResourceIdentifier} values
   * can round-trip identifier meta. When the caller's configuration cannot deserialize a present
   * {@code Optional}, the derived binder mapper also registers the pinned JDK 8 datatype module so
   * the supported cross-major Optional contract holds on the default configured-mapper path;
   * caller-supplied Optional handling is detected behaviorally and always wins. Does not register
   * the JSON:API document module because the binder produces application values, not serialized
   * output.
   */
  private static JsonMapper resourceBindingMapper(JsonMapper base) {
    JsonMapper.Builder derived = base.rebuild().addModule(new MetaBindingModule());
    if (!supportsOptionalDeserialization(base)) {
      derived = derived.addModule(new Jdk8Module());
    }
    return derived.build();
  }

  /**
   * Derives a new mapper with JSON:API document serializers registered. Package-private so callers
   * cannot serialize documents without aggregate validation.
   */
  static JsonMapper documentMapper(JsonMapper base) {
    Objects.requireNonNull(base, "base");
    return base.rebuild().addModule(new JsonApiDocumentModule()).build();
  }
}
