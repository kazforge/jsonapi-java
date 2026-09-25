package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.mapping.internal.MappingDefinitionInvariants;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.ClassIntrospector;
import tools.jackson.databind.json.JsonMapper;

/**
 * Canonical configured-Jackson authority for class-level JSON:API resource metadata and full {@link
 * ResourceMapping} definitions. All class-level {@code @JsonApiResource} interpretation flows
 * through {@link MappingDefinitionResolver} against mapper-introspected direct class annotations,
 * so configured target-specific mix-ins are authoritative without importing resource metadata from
 * supertypes or interfaces. Full Jackson property introspection remains separate and continues to
 * use the ordinary class view.
 */
public final class MappingDefinitionCache {

  private final JsonMapper mapper;
  private final Map<CacheKey, ResourceMapping> cache = new ConcurrentHashMap<>();
  private final Map<CacheKey, ValidatedMapping> validatedCache = new ConcurrentHashMap<>();
  private final Map<CacheKey, ReadResourceMapping> readCache = new ConcurrentHashMap<>();
  private final Map<CacheKey, Optional<String>> resourceTypeNames = new ConcurrentHashMap<>();

  public MappingDefinitionCache(JsonMapper mapper) {
    this.mapper = mapper;
  }

  JavaType constructType(Class<?> rawType) {
    return mapper.constructType(rawType);
  }

  JavaType specializeType(JavaType declaredType, Class<?> runtimeType) {
    return mapper.getTypeFactory().constructSpecializedType(declaredType, runtimeType);
  }

  /**
   * Resolves the mapping for {@code javaType}, preserving full parameterization so property types
   * introspect with type variables bound (for example {@code GenericPatch<String>} resolves its
   * members as {@code String}). The cache key already holds the {@link JavaType}, so distinct
   * parameterizations of the same raw class map independently.
   */
  ResourceMapping resolve(JavaType javaType) {
    SerializationConfig config = mapper.serializationConfig();
    int configHash = configHash(config);
    CacheKey key = new CacheKey(javaType, configHash);
    ResourceMapping existing = cache.get(key);
    if (existing != null) {
      return existing;
    }
    Class<?> rawType = javaType.getRawClass();
    return cache.computeIfAbsent(key, cacheKey -> computeMapping(rawType, javaType, config));
  }

  /**
   * Resolves a write mapping and memoizes the generic-member validation result using the same
   * complete-type and serialization-configuration key as the mapping cache.
   */
  ValidatedMapping resolveValidated(JavaType javaType) {
    SerializationConfig config = mapper.serializationConfig();
    CacheKey key = new CacheKey(javaType, configHash(config));
    return validatedCache.computeIfAbsent(
        key,
        cacheKey -> {
          ResourceMapping mapping =
              cache.computeIfAbsent(
                  cacheKey, ignored -> computeMapping(javaType.getRawClass(), javaType, config));
          return new ValidatedMapping(
              mapping,
              Optional.ofNullable(ResolvedTypeSupport.findUnresolvedProperty(mapping, javaType)));
        });
  }

  /**
   * Resolves the deserialization-oriented property view used by ordinary flat reads. This cache is
   * deliberately separate from {@link #cache}: write mapping remains serialization-oriented and
   * continues to own resource writing.
   */
  ReadResourceMapping resolveRead(JavaType javaType) {
    DeserializationConfig config = mapper.deserializationConfig();
    CacheKey key = new CacheKey(javaType, configHash(config));
    ReadResourceMapping existing = readCache.get(key);
    if (existing != null) {
      return existing;
    }
    Class<?> rawType = javaType.getRawClass();
    return readCache.computeIfAbsent(
        key, cacheKey -> computeReadMapping(rawType, javaType, config));
  }

  /** Resolves the configured class-level resource type name for a complete Java type. */
  public @Nullable String findResourceTypeName(JavaType javaType) {
    CacheKey key = new CacheKey(javaType, configHash(mapper.serializationConfig()));
    Optional<String> existing =
        resourceTypeNames.computeIfAbsent(
            key, cacheKey -> Optional.ofNullable(computeResourceTypeName(javaType)));
    return existing.orElse(null);
  }

  /**
   * Resolves and validates the configured class-level resource type name for {@code rawType}
   * through mapper-aware introspection (class-level mix-ins honored).
   *
   * @throws com.kazforge.jsonapi.diagnostic.JsonApiMappingException {@link
   *     com.kazforge.jsonapi.diagnostic.MappingDiagnostic#MISSING_RESOURCE_ANNOTATION} when no
   *     configured metadata exists, or {@code INVALID_RESOURCE_TYPE} when the name is empty or
   *     invalid
   */
  public String requireResourceTypeName(Class<?> rawType) {
    return requireResourceTypeName(mapper.constructType(rawType));
  }

  /**
   * Resolves and validates the configured class-level resource type name for a complete Java type.
   */
  public String requireResourceTypeName(JavaType javaType) {
    return MappingDefinitionInvariants.requireResourceTypeName(
        findResourceTypeName(javaType), javaType.getRawClass());
  }

  private @Nullable String computeResourceTypeName(JavaType javaType) {
    ClassIntrospector introspector = mapper.serializationConfig().classIntrospectorInstance();
    AnnotatedClass annotatedClass = introspector.introspectDirectClassAnnotations(javaType);
    return MappingDefinitionResolver.resourceTypeName(annotatedClass);
  }

  private ResourceMapping computeMapping(
      Class<?> rawType, JavaType javaType, SerializationConfig config) {
    ClassIntrospector introspector = config.classIntrospectorInstance();
    AnnotatedClass annotatedClass = introspector.introspectClassAnnotations(javaType);
    AnnotatedClass resourceMetadata = introspector.introspectDirectClassAnnotations(javaType);
    BeanDescription beanDescription =
        introspector.introspectForSerialization(javaType, annotatedClass);
    return MappingDefinitionResolver.resolve(beanDescription, rawType, resourceMetadata);
  }

  private ReadResourceMapping computeReadMapping(
      Class<?> rawType, JavaType javaType, DeserializationConfig config) {
    ClassIntrospector deserializationIntrospector = config.classIntrospectorInstance();
    AnnotatedClass annotatedClass =
        deserializationIntrospector.introspectClassAnnotations(javaType);
    AnnotatedClass resourceMetadata =
        deserializationIntrospector.introspectDirectClassAnnotations(javaType);
    BeanDescription deserializationDescription =
        deserializationIntrospector.introspectForDeserialization(javaType, annotatedClass);

    SerializationConfig serializationConfig = mapper.serializationConfig();
    ClassIntrospector serializationIntrospector = serializationConfig.classIntrospectorInstance();
    AnnotatedClass serializationAnnotations =
        serializationIntrospector.introspectClassAnnotations(javaType);
    BeanDescription serializationDescription =
        serializationIntrospector.introspectForSerialization(javaType, serializationAnnotations);

    MappingDefinitionResolver.rejectConflicts(
        deserializationDescription, serializationDescription, rawType);
    return MappingDefinitionResolver.resolveRead(
        deserializationDescription,
        serializationDescription,
        rawType,
        resourceMetadata,
        effectiveReadProperties(javaType, deserializationDescription));
  }

  /**
   * Resolves the effective read properties from the actual configured bean deserializer. In
   * particular, this keeps creator parameters, setter-only properties, write-only properties,
   * generic bindings, and property-level type refinement on the deserialization side instead of
   * guessing from a getter. Injection-only and view-excluded properties are absent, so a supplied
   * member for which the configured mapper has no effective deserialization target is not bindable.
   *
   * <p>The configured bean deserializer's effective properties retain their property-scoped value
   * deserializers, which nested construction-path walking needs to stop at custom-deserializer
   * boundaries. The creator-name set covers every non-injection, view-eligible effective creator
   * property, including unannotated ones that are absent from the JSON:API read mapping but still
   * required by the configured creator; those names drive message-independent missing-creator-input
   * classification.
   */
  private EffectiveReadProperties effectiveReadProperties(
      JavaType javaType, BeanDescription description) {
    Map<String, SettableBeanProperty> targets = new java.util.LinkedHashMap<>();
    java.util.Set<String> creatorExternalNames = new java.util.HashSet<>();
    ValueDeserializer<?> deserializer =
        mapper._deserializationContext().findNonContextualValueDeserializer(javaType);
    if (!(deserializer instanceof BeanDeserializerBase bean)) {
      return new EffectiveReadProperties(targets, creatorExternalNames);
    }
    Class<?> activeView = mapper.deserializationConfig().getActiveView();
    for (var definition : description.findProperties()) {
      SettableBeanProperty property =
          bean.findProperty(PropertyName.construct(definition.getFullName().getSimpleName()));
      if (property != null
          && !property.isInjectionOnly()
          && (activeView == null || property.visibleInView(activeView))) {
        targets.put(definition.getName(), property);
      }
    }
    for (var creators = bean.creatorProperties(); creators.hasNext(); ) {
      SettableBeanProperty property = creators.next();
      if (!property.isInjectionOnly()
          && (activeView == null || property.visibleInView(activeView))) {
        creatorExternalNames.add(property.getName());
      }
    }
    return new EffectiveReadProperties(targets, java.util.Set.copyOf(creatorExternalNames));
  }

  /** Effective read properties plus the external names of every eligible effective creator. */
  record EffectiveReadProperties(
      Map<String, SettableBeanProperty> byExternalName,
      java.util.Set<String> creatorExternalNames) {}

  /**
   * Identity fragment shared by serialization and deserialization cache keys: naming strategy and
   * default visibility. The two {@code configHash} overloads stay separate because the caches
   * answer different questions; deserialization also includes the active view.
   */
  private static int configIdentity(MapperConfig<?> config) {
    int result =
        config.getPropertyNamingStrategy() != null
            ? config.getPropertyNamingStrategy().hashCode()
            : 0;
    return 31 * result + config.getDefaultVisibilityChecker().hashCode();
  }

  private static int configHash(SerializationConfig config) {
    return configIdentity(config);
  }

  private static int configHash(DeserializationConfig config) {
    int result = configIdentity(config);
    Class<?> activeView = config.getActiveView();
    return 31 * result + (activeView == null ? 0 : activeView.hashCode());
  }

  record ValidatedMapping(ResourceMapping mapping, Optional<MappingProperty> unresolvedProperty) {}

  private record CacheKey(JavaType type, int configHash) {}
}
