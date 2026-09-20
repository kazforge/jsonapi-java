package com.kazforge.jsonapi.jackson2;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.core.aggregate.ValidationContext;
import com.kazforge.jsonapi.document.DocumentReadContext;
import com.kazforge.jsonapi.jackson2.internal.DomainResourceBinder;
import com.kazforge.jsonapi.jackson2.internal.DomainResourceWriter;
import com.kazforge.jsonapi.jackson2.internal.MappingDefinitionCache;
import com.kazforge.jsonapi.jackson2.mapping.RelationshipLinkageMapper;
import com.kazforge.jsonapi.mapping.IdentifierConverter;
import com.kazforge.jsonapi.mapping.ResourceDecoratorRegistry;
import com.kazforge.jsonapi.mapping.ResourceTypeRegistry;
import java.util.Map;
import java.util.Objects;

/**
 * Factory for the Jackson 2 JSON:API document writer, validated document reader, resource mapper,
 * flat DTO resource binder, typed domain envelope reader, and presence-aware PATCH readers.
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
 * JsonApiResourceBinder}, {@link JsonApiDomainDocumentReader}, {@link JsonApiPatchCommandReader},
 * and {@link JsonApiPatchDtoReader}; ordinary application code can instead use the configured
 * {@link JsonApi} runtime returned by {@link #jsonApi(JsonMapper)} or {@link #builder(JsonMapper)}.
 */
public final class JsonApiJackson2 {

  private static final String CONTEXT = "context";
  private static final String IDENTIFIER_CONVERTER = "identifierConverter";
  private static final String LINKAGE_MAPPERS = "linkageMappers";

  private JsonApiJackson2() {}

  /**
   * Returns a Level-1 configured runtime with documented defaults: default identifier conversion,
   * no custom linkage mappers, the default representation policy, no resource decorators, and no
   * resource-write {@code jsonapi.version} default.
   */
  public static Jackson2JsonApi jsonApi(JsonMapper base) {
    Objects.requireNonNull(base, "base");
    return builder(base).build();
  }

  /**
   * Returns a builder for a Level-1 configured runtime over the given configured mapper. Only
   * coherent application-lifetime configuration belongs on the builder; request-scoped values stay
   * per-operation arguments on the resulting runtime.
   */
  public static Jackson2JsonApi.Builder builder(JsonMapper base) {
    Objects.requireNonNull(base, "base");
    return new Jackson2JsonApi.Builder(base);
  }

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
   * <p>Decorators add only {@code ResourceObject.links} and {@code Relationship.links} for mapped
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
   * Returns a typed domain envelope reader with default identifier conversion and no custom
   * relationship linkage mappers. Document decoding/validation behaves exactly like {@link
   * #reader(JsonMapper, DocumentReadContext)}; primary and included resources bind through the flat
   * DTO binder after a {@link ResourceTypeRegistry} lookup, using a mapper derived via {@link
   * JsonMapper#rebuild()} that never mutates the caller's mapper.
   */
  public static JsonApiDomainDocumentReader domainDocumentReader(
      JsonMapper base, DocumentReadContext context, ResourceTypeRegistry registry) {
    return domainDocumentReader(base, context, registry, IdentifierConverter.defaults(), Map.of());
  }

  /**
   * Returns a typed domain envelope reader with the given identifier converter and no custom
   * relationship linkage mappers. Derives a new mapper via {@link JsonMapper#rebuild()} and never
   * mutates the caller's mapper.
   */
  public static JsonApiDomainDocumentReader domainDocumentReader(
      JsonMapper base,
      DocumentReadContext context,
      ResourceTypeRegistry registry,
      IdentifierConverter identifierConverter) {
    return domainDocumentReader(base, context, registry, identifierConverter, Map.of());
  }

  /**
   * Returns a typed domain envelope reader with the given identifier converter and relationship
   * linkage mappers keyed by relationship target class. Derives a new mapper via {@link
   * JsonMapper#rebuild()} and never mutates the caller's mapper.
   */
  public static JsonApiDomainDocumentReader domainDocumentReader(
      JsonMapper base,
      DocumentReadContext context,
      ResourceTypeRegistry registry,
      IdentifierConverter identifierConverter,
      Map<Class<?>, RelationshipLinkageMapper> linkageMappers) {
    Objects.requireNonNull(base, "base");
    Objects.requireNonNull(context, CONTEXT);
    Objects.requireNonNull(registry, "registry");
    Objects.requireNonNull(identifierConverter, IDENTIFIER_CONVERTER);
    Objects.requireNonNull(linkageMappers, LINKAGE_MAPPERS);
    return new JsonApiDomainDocumentReader(
        base, context, registry, identifierConverter, linkageMappers);
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

  private static JsonMapper resourceMappingMapper(JsonMapper base) {
    return JsonApiJackson2Assembly.resourceMappingMapper(base);
  }

  static JsonMapper resourceBindingMapper(JsonMapper base) {
    return JsonApiJackson2Assembly.resourceBindingMapper(base);
  }

  static JsonMapper documentMapper(JsonMapper base) {
    return JsonApiJackson2Assembly.documentMapper(base);
  }
}
