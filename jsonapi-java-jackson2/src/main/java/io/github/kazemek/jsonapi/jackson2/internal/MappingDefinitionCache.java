package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.ClassIntrospector;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Canonical configured-Jackson authority for class-level JSON:API resource metadata, full {@link
 * ResourceMapping} definitions, and deserialization-oriented {@link ReadResourceMapping}
 * definitions for one derived mapping mapper. All class-level {@code JsonApiResource}
 * interpretation flows through {@link MappingDefinitionResolver} against mapper-introspected direct
 * class annotations, so configured target-specific mix-ins are authoritative without importing
 * resource metadata from supertypes or interfaces.
 *
 * <p>The caches are owned by the mapper instance they were created for; entries are keyed by the
 * complete {@link JavaType} so distinct parameterizations of the same raw class map independently.
 * There is deliberately no cross-mapper cache and no partial configuration hash: configuration is
 * already isolated by mapper instance, and each derived mapper is immutable after construction.
 * Write mapping remains serialization-oriented and continues to own resource writing; the separate
 * read cache resolves the deserialization-oriented view used by ordinary flat reads.
 */
public final class MappingDefinitionCache {

  private final JsonMapper mapper;
  private final Map<JavaType, ValidatedMapping> validatedCache = new ConcurrentHashMap<>();
  private final Map<JavaType, ReadResourceMapping> readCache = new ConcurrentHashMap<>();
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

  /**
   * Resolves the deserialization-oriented property view used by ordinary flat reads. This cache is
   * deliberately separate from {@link #validatedCache}: write mapping remains
   * serialization-oriented and continues to own resource writing.
   */
  ReadResourceMapping resolveRead(JavaType javaType) {
    return readCache.computeIfAbsent(javaType, this::computeReadMapping);
  }

  private ReadResourceMapping computeReadMapping(JavaType javaType) {
    DeserializationView view = deserializationView(javaType);
    SerializationConfig serializationConfig = mapper.getSerializationConfig();
    BeanDescription serializationDescription =
        introspectSerialization(serializationConfig, javaType);
    return MappingDefinitionResolver.resolveRead(
        view.description(),
        serializationDescription,
        javaType.getRawClass(),
        view.resourceMetadata(),
        effectiveDeserializationTypes(javaType, view.description()));
  }

  private record DeserializationView(
      BeanDescription description, AnnotatedClass resourceMetadata) {}

  private DeserializationView deserializationView(JavaType javaType) {
    DeserializationConfig config = mapper.getDeserializationConfig();
    ClassIntrospector introspector = config.getClassIntrospector();
    AnnotatedClass resourceMetadata =
        introspector.forDirectClassAnnotations(config, javaType, config).getClassInfo();
    BeanDescription description = introspector.forDeserialization(config, javaType, config);
    return new DeserializationView(description, resourceMetadata);
  }

  private BeanDescription introspectSerialization(SerializationConfig config, JavaType javaType) {
    ClassIntrospector introspector = config.getClassIntrospector();
    return introspector.forSerialization(config, javaType, config);
  }

  /**
   * Resolves property types from the actual configured bean deserializer. In particular, this keeps
   * creator parameters, setter-only properties, write-only properties, generic bindings, and
   * property-level type refinement on the deserialization side instead of guessing from a getter.
   */
  private Map<String, JavaType> effectiveDeserializationTypes(
      JavaType javaType, BeanDescription description) {
    Map<String, JavaType> targets = new LinkedHashMap<>();
    DeserializationConfig config = mapper.getDeserializationConfig();
    DefaultDeserializationContext context =
        ((DefaultDeserializationContext) mapper.getDeserializationContext())
            .createInstance(config, null, null);
    com.fasterxml.jackson.databind.JsonDeserializer<?> deserializer;
    try {
      deserializer = context.findNonContextualValueDeserializer(javaType);
    } catch (com.fasterxml.jackson.databind.JsonMappingException e) {
      throw new IllegalStateException("Failed to resolve a deserializer for " + javaType, e);
    }
    if (!(deserializer instanceof BeanDeserializerBase bean)) {
      return targets;
    }
    Class<?> activeView = config.getActiveView();
    for (var definition : description.findProperties()) {
      SettableBeanProperty property = bean.findProperty(definition.getFullName().getSimpleName());
      if (property != null
          && !property.isInjectionOnly()
          && (activeView == null || property.visibleInView(activeView))) {
        targets.put(definition.getName(), property.getType());
      }
    }
    return targets;
  }

  record ValidatedMapping(ResourceMapping mapping, Optional<MappingProperty> unresolvedProperty) {}
}
