package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.ClassIntrospector;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Canonical configured-Jackson authority for class-level JSON:API resource metadata and full {@link
 * ResourceMapping} definitions for one derived mapping mapper. All class-level {@code
 * JsonApiResource} interpretation flows through {@link MappingDefinitionResolver} against
 * mapper-introspected direct class annotations, so configured target-specific mix-ins are
 * authoritative without importing resource metadata from supertypes or interfaces.
 *
 * <p>The cache is owned by the mapper instance it was created for; entries are keyed by the
 * complete {@link JavaType} so distinct parameterizations of the same raw class map independently.
 * There is deliberately no cross-mapper cache and no partial configuration hash: configuration is
 * already isolated by mapper instance, and each derived mapper is immutable after construction.
 */
public final class MappingDefinitionCache {

  private final JsonMapper mapper;
  private final Map<JavaType, ValidatedMapping> validatedCache = new ConcurrentHashMap<>();
  private final Map<JavaType, Optional<String>> resourceTypeNames = new ConcurrentHashMap<>();

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
   * Resolves a write mapping and memoizes the generic-member validation result under the
   * complete-type key. Property types introspect with type variables bound, so distinct
   * parameterizations of the same raw class map independently.
   */
  ValidatedMapping resolveValidated(JavaType javaType) {
    return validatedCache.computeIfAbsent(
        javaType,
        type -> {
          ResourceMapping mapping =
              computeMapping(type.getRawClass(), type, mapper.getSerializationConfig());
          return new ValidatedMapping(
              mapping,
              Optional.ofNullable(ResolvedTypeSupport.findUnresolvedProperty(mapping, type)));
        });
  }

  /** Resolves the configured class-level resource type name for a complete Java type. */
  public @Nullable String findResourceTypeName(JavaType javaType) {
    Optional<String> existing =
        resourceTypeNames.computeIfAbsent(
            javaType, type -> Optional.ofNullable(computeResourceTypeName(type)));
    return existing.orElse(null);
  }

  private @Nullable String computeResourceTypeName(JavaType javaType) {
    ClassIntrospector introspector = mapper.getSerializationConfig().getClassIntrospector();
    AnnotatedClass annotatedClass =
        introspector
            .forDirectClassAnnotations(
                mapper.getSerializationConfig(), javaType, mapper.getSerializationConfig())
            .getClassInfo();
    return MappingDefinitionResolver.resourceTypeName(annotatedClass);
  }

  private ResourceMapping computeMapping(
      Class<?> rawType, JavaType javaType, SerializationConfig config) {
    ClassIntrospector introspector = config.getClassIntrospector();
    AnnotatedClass resourceMetadata =
        introspector.forDirectClassAnnotations(config, javaType, config).getClassInfo();
    BeanDescription beanDescription = introspector.forSerialization(config, javaType, config);
    return MappingDefinitionResolver.resolve(beanDescription, rawType, resourceMetadata);
  }

  record ValidatedMapping(ResourceMapping mapping, Optional<MappingProperty> unresolvedProperty) {}
}
