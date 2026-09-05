package io.github.kazemek.jsonapi.jackson3;

import io.github.kazemek.jsonapi.core.validation.ValidationContext;
import io.github.kazemek.jsonapi.jackson.document.DocumentReadContext;
import io.github.kazemek.jsonapi.jackson.mapping.IdentifierConverter;
import io.github.kazemek.jsonapi.jackson.mapping.ResourceDecoratorRegistry;
import io.github.kazemek.jsonapi.jackson3.internal.DomainResourceBinder;
import io.github.kazemek.jsonapi.jackson3.internal.DomainResourceWriter;
import io.github.kazemek.jsonapi.jackson3.internal.JsonApiDocumentModule;
import io.github.kazemek.jsonapi.jackson3.internal.MappingDefinitionCache;
import io.github.kazemek.jsonapi.jackson3.internal.MetaBindingModule;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.json.JsonMapper;

/**
 * Factory for Jackson 3 JSON:API document writers, readers, resource mappers, flat DTO binders, and
 * presence-aware PATCH readers.
 *
 * <p>Callers supply an already-configured {@link JsonMapper}. Each canonical factory accepts that
 * mapper first, followed by the policy, context, and collaborators required by its capability.
 * Convenience factories select documented defaults and delegate to the canonical seam; mapper
 * builders are intentionally not accepted. Factory construction never mutates or replaces the
 * caller's configuration in place. Token-driven document reading uses the supplied mapper directly.
 * Capabilities that need adapter-specific modules or isolated introspection state derive a mapper
 * via {@link JsonMapper#rebuild()}. Public surface consists of {@link Jackson3JsonApi}, {@link
 * JsonApiDocumentWriter}, {@link JsonApiDocumentReader}, {@link JsonApiResourceMapper}, {@link
 * JsonApiResourceBinder}, {@link JsonApiDomainDocumentReader}, {@link JsonApiPatchCommandReader},
 * and {@link JsonApiPatchDtoReader}.
 *
 * <p>Ordinary application code should prefer the Level-1 configured runtime: {@link
 * #jsonApi(JsonMapper)} for documented defaults or {@link #builder(JsonMapper)} for coherent
 * application-lifetime configuration. The capability factories below remain the advanced
 * mechanism/control seams.
 */
public final class JsonApiJackson3 {

  private static final String CONTEXT = "context";
  private static final String IDENTIFIER_CONVERTER = "identifierConverter";
  private static final String LINKAGE_MAPPERS = "linkageMappers";

  private JsonApiJackson3() {}

  /**
   * Returns a Level-1 configured runtime with documented defaults: default identifier conversion,
   * no custom linkage mappers, the default representation policy, and no resource decorators.
   */
  public static Jackson3JsonApi jsonApi(JsonMapper base) {
    Objects.requireNonNull(base, "base");
    return builder(base).build();
  }

  /**
   * Returns a builder for a Level-1 configured runtime over the given configured mapper. Only
   * coherent application-lifetime configuration belongs on the builder; request-scoped values stay
   * per-operation arguments on the resulting runtime.
   */
  public static Jackson3JsonApi.Builder builder(JsonMapper base) {
    Objects.requireNonNull(base, "base");
    return new Jackson3JsonApi.Builder(base);
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
    JsonMapper derived = resourceMappingMapper(base);
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

  /**
   * Derives a mapper for resource mapping introspection, attribute conversion, and binder
   * construction. Registers {@link MetaBindingModule} so built-in {@code ResourceIdentifier} values
   * can round-trip identifier meta. Does not register the JSON:API document module because the
   * resource mapper produces core model objects, not serialized output.
   */
  private static JsonMapper resourceMappingMapper(JsonMapper base) {
    return base.rebuild().addModule(new MetaBindingModule()).build();
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
