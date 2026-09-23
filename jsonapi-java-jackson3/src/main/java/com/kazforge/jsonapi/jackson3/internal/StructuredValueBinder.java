package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.mapping.internal.StructuredPatchBinder;
import com.kazforge.jsonapi.mapping.internal.StructuredShape;
import com.kazforge.jsonapi.mapping.internal.StructuredShapeBackend;
import com.kazforge.jsonapi.patch.PatchPresence;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.introspect.ClassIntrospector;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 native bridge for the shared recursive structured-value PATCH binder.
 *
 * <p>The neutral traversal policy (typed marker-tree assembly, low-level {@code StructuredPatch}
 * assembly, presence/null/empty distinctions, unknown-member strictness versus skip, and pointer
 * accumulation) is owned by {@link StructuredPatchBinder}. This adapter supplies only native
 * mechanics through {@link StructuredShapeBackend}: shape resolution from configured
 * deserialization introspection, the {@code PatchPresence}/{@code Optional}/primitive type queries,
 * and atomic conversion through {@link PropertyScopedValueConverter}. It also owns the adapter-side
 * construction-path translation that walks resolved typed shapes.
 *
 * <p>Shape resolution is cached per {@link JavaType} plus the deserialization-config hash (naming
 * strategy and visibility checker), independently of the serialization-keyed resource-mapping
 * cache. Nested shape declarations are validated lazily: an invalid nested shape is only rejected
 * when it is actually bound, so arbitrary type graphs are not traversed for declaration validation.
 */
final class StructuredValueBinder implements StructuredShapeBackend<JavaType> {

  private static final StructuredShape<JavaType> NO_SHAPE = new StructuredShape<>(List.of());

  private final JsonMapper mapper;
  private final PropertyScopedValueConverter propertyScoped;
  private final StructuredPatchBinder<JavaType> shared;
  private final Map<CacheKey, StructuredShape<JavaType>> shapeCache = new ConcurrentHashMap<>();

  StructuredValueBinder(JsonMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.propertyScoped = new PropertyScopedValueConverter(mapper);
    this.shared = new StructuredPatchBinder<>(this);
  }

  /** Binds one supplied member value for the typed PATCH DTO path. */
  @Nullable Object typedMemberValue(
      @Nullable Object wire,
      JavaType declaredPatchPresenceType,
      MappingLocation pointer,
      Class<?> rawType) {
    return shared.typedMemberValue(wire, declaredPatchPresenceType, pointer, rawType);
  }

  /**
   * Decides whether one supplied low-level member value recurses into a {@code StructuredPatch}.
   */
  StructuredPatchBinder.LowLevelKind lowLevelKind(
      JavaType declaredType,
      @Nullable Object wire,
      @Nullable AnnotatedMember serializationMember,
      @Nullable AnnotatedMember deserializationMember,
      MappingLocation pointer,
      Class<?> rawType) {
    boolean customization =
        hasDeserializationCustomization(declaredType, serializationMember, deserializationMember);
    return shared.lowLevelKind(declaredType, wire, customization, pointer, rawType);
  }

  /** Recursively binds one supplied object wire value into a low-level {@code StructuredPatch}. */
  Object bindLowLevelStructured(
      @Nullable Object wire, JavaType declaredType, MappingLocation pointer, Class<?> rawType) {
    return shared.bindLowLevelStructured(wire, declaredType, pointer, rawType);
  }

  /**
   * Translates a failed bean-construction Jackson path into a resource-relative mapping location.
   * The path's first name selects the member's start through {@code startsByLogicalName} (Jackson
   * logical name to wire prefix); deeper names are walked through resolved presence-aware shape
   * metadata, each matching member contributing its escaped wire-name segment. Walking stops at the
   * first name that is not a shape member, so Jackson-internal names below an atomic member are
   * never leaked into the location. The internal presence-marker {@code value} member between two
   * presence-aware shape levels is skipped. Returns {@code null} when the path is empty or its
   * first name matches no mapped member — an absent location per the mapping-location contract,
   * never a Jackson logical property name.
   */
  @Nullable MappingLocation translateConstructionPath(
      List<String> names, Map<String, MappingConstructionStart> startsByLogicalName) {
    if (names.isEmpty()) {
      return null;
    }
    MappingConstructionStart start = startsByLogicalName.get(names.getFirst());
    if (start == null) {
      return null;
    }
    MappingLocation pointer = start.location();
    JavaType current = start.declaredType();
    boolean walking = true;
    for (int i = 1; i < names.size() && walking; i++) {
      String name = names.get(i);
      StructuredShape<JavaType> shape = shared.typedShape(current);
      if (shape == null) {
        walking = false;
      } else {
        StructuredShape.Member<JavaType> member = shape.memberByWire(name);
        if (member != null) {
          pointer = pointer.append(member.wireName());
          current = member.declaredType();
        } else if (!"value".equals(name)) {
          walking = false;
        }
      }
    }
    return pointer;
  }

  // ============================== StructuredShapeBackend ==============================

  @Override
  public @Nullable StructuredShape<JavaType> shapeOf(JavaType type) {
    DeserializationConfig config = mapper.deserializationConfig();
    CacheKey key = new CacheKey(type, configHash(config));
    StructuredShape<JavaType> cached = shapeCache.get(key);
    if (cached != null) {
      return cached == NO_SHAPE ? null : cached;
    }
    StructuredShape<JavaType> result =
        shapeCache.computeIfAbsent(
            key,
            ignored -> {
              StructuredShape<JavaType> computed = computeShape(type, config);
              return computed == null ? NO_SHAPE : computed;
            });
    return result == NO_SHAPE ? null : result;
  }

  @Override
  public boolean isPatchPresence(JavaType type) {
    return type.getRawClass() == PatchPresence.class && type.containedTypeCount() == 1;
  }

  @Override
  public JavaType patchPresenceInner(JavaType type) {
    return type.containedType(0);
  }

  @Override
  public boolean isOptional(JavaType type) {
    return type.getRawClass() == Optional.class && type.containedTypeCount() == 1;
  }

  @Override
  public JavaType optionalInner(JavaType type) {
    return type.containedType(0);
  }

  @Override
  public boolean isPrimitive(JavaType type) {
    return type.isPrimitive();
  }

  @Override
  public String typeName(JavaType type) {
    return type.toCanonical();
  }

  @Override
  public @Nullable Object convertAtomic(
      JavaType beanType,
      JavaType declaredType,
      JavaType targetType,
      String wireName,
      @Nullable Object wire,
      MappingLocation pointer,
      Class<?> rawType) {
    try {
      return propertyScoped.convert(beanType, wireName, declaredType, targetType, wire);
    } catch (RuntimeException e) {
      throw new JsonApiMappingException(
          MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE,
          rawType,
          pointer,
          "Failed to convert the nested structured value at '" + pointer + "'",
          e);
    }
  }

  // ============================== SHAPE RESOLUTION ==============================

  private @Nullable StructuredShape<JavaType> computeShape(
      JavaType type, DeserializationConfig config) {
    DeserializationContext context = mapper._deserializationContext();
    ValueDeserializer<?> deserializer = context.findRootValueDeserializer(type);
    if (!(deserializer instanceof BeanDeserializerBase)) {
      return null;
    }
    ClassIntrospector introspector = config.classIntrospectorInstance();
    AnnotatedClass annotatedClass = introspector.introspectClassAnnotations(type);
    BeanDescription beanDescription =
        introspector.introspectForDeserialization(type, annotatedClass);
    List<StructuredShape.Member<JavaType>> members = new ArrayList<>();
    for (BeanPropertyDefinition definition : beanDescription.findProperties()) {
      if (definition.getMutator() == null && !definition.hasConstructorParameter()) {
        continue;
      }
      JavaType memberType = definition.getPrimaryType();
      AnnotatedMember serializationMember = definition.getAccessor();
      AnnotatedMember deserializationMember = definition.getMutator();
      members.add(
          new StructuredShape.Member<>(
              definition.getInternalName(),
              definition.getFullName().getSimpleName(),
              memberType,
              WrapperCustomization.isPatchPresence(memberType),
              WrapperCustomization.isPresenceAttempt(memberType),
              WrapperCustomization.has(
                  mapper, memberType, serializationMember, deserializationMember),
              hasDeserializationCustomization(
                  memberType, serializationMember, deserializationMember)));
    }
    return new StructuredShape<>(members);
  }

  private static int configHash(DeserializationConfig config) {
    int result =
        config.getPropertyNamingStrategy() != null
            ? config.getPropertyNamingStrategy().hashCode()
            : 0;
    result = 31 * result + config.getDefaultVisibilityChecker().hashCode();
    return result;
  }

  /**
   * True when the property carries property-scoped deserialization customization on its
   * serialization-side or deserialization-side member. Jackson may surface a deserialization
   * annotation through the accessor (getter / field), the mutator (creator parameter / setter /
   * field), or both, so both are inspected to honor setter-, creator-, field-, and getter-placed
   * customization as normal Jackson binding would. {@code declaredType} is the member's resolved
   * property {@link JavaType}, used as the base for type-refinement checks (never {@code
   * AnnotatedMember#getType()}, which is {@code void} for setters).
   */
  private boolean hasDeserializationCustomization(
      JavaType declaredType,
      @Nullable AnnotatedMember serializationMember,
      @Nullable AnnotatedMember deserializationMember) {
    return WrapperCustomization.hasDeserialization(mapper, declaredType, serializationMember)
        || WrapperCustomization.hasDeserialization(mapper, declaredType, deserializationMember);
  }

  private record CacheKey(JavaType type, int configHash) {}
}
