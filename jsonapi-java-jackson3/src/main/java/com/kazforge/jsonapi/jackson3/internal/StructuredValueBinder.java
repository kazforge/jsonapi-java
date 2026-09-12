package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.jackson.diagnostic.MappingLocation;
import com.kazforge.jsonapi.jackson.internal.patch.PresenceMarker;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;
import com.kazforge.jsonapi.jackson.patch.StructuredMember;
import com.kazforge.jsonapi.jackson.patch.StructuredMemberState;
import com.kazforge.jsonapi.jackson.patch.StructuredPatch;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * Location-agnostic recursive structured-value PATCH engine shared by the low-level {@code
 * PatchCommand} path and the direct typed PATCH DTO path.
 *
 * <p>The engine owns member resolution (deserialization-side Jackson introspection), shape and
 * boundary classification, low-level nested conversion, typed marker-tree construction, null
 * policy, wire-pointer accumulation, and lazy nested declaration validation. It has no {@link
 * ResourceMapping} / {@link MappingProperty} / {@code @JsonApiAttribute} / {@link
 * com.kazforge.jsonapi.jackson.patch.PatchChange} dependency: callers supply the declared {@link
 * JavaType}, wire value, starting pointer, and (low-level) accessor, so a later structured JSON:API
 * {@code meta} mapping can reuse the same machinery at its own location with a stricter outer-state
 * policy (ADR-014).
 *
 * <p>Two modes (ADR-014): the typed mode recurses only through deliberately presence-aware nested
 * PATCH shapes (every visible member exactly {@code PatchPresence<T>}, no wrapper-level
 * customization); the low-level mode derives supplied-only nested changes from ordinary structured
 * domain value types under the traversable-bean + object-wire boundary, with {@link Optional} as a
 * transparent qualification wrapper and a single {@code PatchPresence} wrapper unwrap.
 * Presence-aware PATCH shapes are a typed-path concept: on the low-level path a {@code
 * PatchPresence<T>} member whose inner type is a presence-aware shape fails loudly with {@link
 * MappingDiagnostic#INVALID_PATCH_PROPERTY_TYPE} at the accumulated pointer.
 *
 * <p>Shape resolution is cached per {@link JavaType} plus the deserialization-config hash (naming
 * strategy and visibility checker), independently of the serialization-keyed resource-mapping
 * cache. Nested shape declarations are validated lazily: an invalid nested shape is only rejected
 * when it is actually bound, so arbitrary type graphs are not traversed for declaration validation.
 */
final class StructuredValueBinder {

  /** Low-level traversal decision for one member. */
  enum LowLevelKind {
    /** The declared type traverses into a {@link StructuredPatch} for this object wire value. */
    RECURSE,
    /** The member stays an atomic converted value. */
    ATOMIC
  }

  private static final Shape NO_SHAPE = new Shape(null, List.of(), false, false);

  private final JsonMapper mapper;
  private final PropertyScopedValueConverter propertyScoped;
  private final Map<CacheKey, Shape> shapeCache = new ConcurrentHashMap<>();

  StructuredValueBinder(JsonMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.propertyScoped = new PropertyScopedValueConverter(mapper);
  }

  // ============================== TYPED MODE ==============================

  /**
   * Binds one supplied member value for the typed PATCH DTO path.
   *
   * <p>{@code declaredPatchPresenceType} must be exactly {@code PatchPresence<T>} (top-level
   * members are validated eagerly by the typed binder; nested members by the engine on shape
   * entry). Returns the value to place inside an internal {@link PresenceMarker} with {@code
   * present=true}: the original JSON-compatible atomic value, {@code null}, or a nested marker map
   * that the single whole-tree {@code convertValue} reads through the inner type (preserving the
   * strict marker invariant).
   */
  @Nullable Object typedMemberValue(
      @Nullable Object wire,
      JavaType declaredPatchPresenceType,
      MappingLocation pointer,
      Class<?> rawType) {
    JavaType inner = declaredPatchPresenceType.containedType(0);
    JavaType effective = unwrapOptional(inner);
    if (wire == null) {
      if (effective.isPrimitive()) {
        throw unsupported(
            rawType,
            pointer,
            "Explicit null is not supported for a primitive nested PATCH member at '"
                + pointer
                + "'");
      }
      return null;
    }
    Shape shape = shapeOf(effective);
    if (shape == null) {
      return wire;
    }
    if (shape.presenceAware()) {
      if (wire instanceof Map<?, ?> map) {
        return bindTypedShape(map, shape, pointer, rawType);
      }
      return wire;
    }
    if (shape.mixed()) {
      throw invalidPatchPropertyType(
          rawType,
          pointer,
          "mixed nested PATCH shape: every visible member of "
              + effective.toCanonical()
              + " must be declared exactly as PatchPresence<T> when the shape is used as a nested "
              + "PATCH shape");
    }
    return wire;
  }

  private Map<String, Object> bindTypedShape(
      Map<?, ?> wire, Shape shape, MappingLocation pointer, Class<?> rawType) {
    validateTypedShape(shape, pointer, rawType);
    for (Object key : wire.keySet()) {
      String name = key instanceof String string ? string : String.valueOf(key);
      if (shape.memberByWire(name) == null) {
        throw unknownPatchMember(rawType, pointer.append(name), name);
      }
    }
    Map<String, Object> markerMap = LinkedHashMap.newLinkedHashMap(shape.members().size());
    for (Member member : shape.members()) {
      String wireName = member.wireName();
      if (wire.containsKey(wireName)) {
        markerMap.put(
            wireName,
            new PresenceMarker(
                true,
                typedMemberValue(
                    wire.get(wireName), member.type(), pointer.append(wireName), rawType)));
      } else {
        markerMap.put(wireName, new PresenceMarker(false, null));
      }
    }
    return markerMap;
  }

  /** On shape entry, every visible member must satisfy the strict presence-aware contract. */
  private void validateTypedShape(Shape shape, MappingLocation pointer, Class<?> rawType) {
    for (Member member : shape.members()) {
      if (WrapperCustomization.has(
          mapper, member.type(), member.serializationMember(), member.deserializationMember())) {
        throw invalidPatchPropertyType(
            rawType,
            pointer.append(member.wireName()),
            "nested PATCH member '"
                + member.wireName()
                + "' must not carry wrapper-level @JsonDeserialize/@JsonSerialize customization");
      }
    }
  }

  // ============================== LOW-LEVEL MODE ==============================

  /**
   * Decides whether one supplied member value recurses into a {@link StructuredPatch} on the
   * low-level path. Throws {@link MappingDiagnostic#INVALID_PATCH_PROPERTY_TYPE} when the declared
   * type unwraps to a presence-aware PATCH shape (a typed-path concept).
   */
  LowLevelKind lowLevelKind(
      JavaType declaredType,
      @Nullable Object wire,
      @Nullable AnnotatedMember serializationMember,
      @Nullable AnnotatedMember deserializationMember,
      MappingLocation pointer,
      Class<?> rawType) {
    if (!(wire instanceof Map<?, ?>)) {
      return LowLevelKind.ATOMIC;
    }
    boolean viaPatchPresence = isPatchPresence(declaredType);
    JavaType beanType = unwrapLowLevel(declaredType);
    if (hasDeserializationCustomization(declaredType, serializationMember, deserializationMember)) {
      return LowLevelKind.ATOMIC;
    }
    Shape shape = shapeOf(beanType);
    if (shape == null) {
      return LowLevelKind.ATOMIC;
    }
    if (viaPatchPresence && shape.presenceAware()) {
      throw invalidPatchPropertyType(
          rawType,
          pointer,
          "presence-aware nested PATCH shapes are a typed-path concept: PatchPresence<"
              + beanType.toCanonical()
              + "> is not supported on the low-level PatchCommand<T> path at '"
              + pointer
              + "'");
    }
    return LowLevelKind.RECURSE;
  }

  /**
   * Recursively binds one supplied object wire value into a {@link StructuredPatch} for the
   * low-level path. The caller must have decided {@link LowLevelKind#RECURSE} for this member.
   * Supplied-only nested members are produced in declaration order; unknown wire members are
   * skipped (lossless change-list contract).
   */
  Object bindLowLevelStructured(
      @Nullable Object wire, JavaType declaredType, MappingLocation pointer, Class<?> rawType) {
    JavaType beanType = unwrapLowLevel(declaredType);
    Shape shape = Objects.requireNonNull(shapeOf(beanType), "shape");
    Map<?, ?> wireMap = (Map<?, ?>) Objects.requireNonNull(wire, "wire");
    List<StructuredMember> members = new ArrayList<>();
    for (Member member : shape.members()) {
      String wireName = member.wireName();
      if (wireMap.containsKey(wireName)) {
        members.add(
            bindLowLevelMember(
                wireMap.get(wireName), member, beanType, pointer.append(wireName), rawType));
      }
    }
    return new StructuredPatch(members);
  }

  private StructuredMember bindLowLevelMember(
      @Nullable Object wire,
      Member member,
      JavaType beanType,
      MappingLocation pointer,
      Class<?> rawType) {
    LowLevelKind kind =
        lowLevelKind(
            member.type(),
            wire,
            member.serializationMember(),
            member.deserializationMember(),
            pointer,
            rawType);
    if (kind == LowLevelKind.RECURSE) {
      StructuredPatch nested =
          (StructuredPatch) bindLowLevelStructured(wire, member.type(), pointer, rawType);
      return new StructuredMember(
          member.wireName(),
          member.internalName(),
          new StructuredMemberState.Structured(nested.members()));
    }
    return new StructuredMember(
        member.wireName(),
        member.internalName(),
        new StructuredMemberState.Atomic(atomicLowLevel(wire, member, beanType, pointer, rawType)));
  }

  private @Nullable Object atomicLowLevel(
      @Nullable Object wire,
      Member member,
      JavaType beanType,
      MappingLocation pointer,
      Class<?> rawType) {
    JavaType target = unwrapPatchPresence(member.type());
    if (wire == null && target.isPrimitive()) {
      throw unsupported(
          rawType,
          pointer,
          "Explicit null is not supported for a primitive nested member at '" + pointer + "'");
    }
    try {
      return propertyScoped.convert(beanType, member.wireName(), member.type(), target, wire);
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

  /**
   * Resolves the structured shape of {@code type} when it is a traversable bean, or {@code null}
   * for atomic types (scalars, containers, custom/scalar deserializers, unresolved types).
   */
  private @Nullable Shape shapeOf(JavaType type) {
    DeserializationConfig config = mapper.deserializationConfig();
    CacheKey key = new CacheKey(type, configHash(config));
    Shape cached = shapeCache.get(key);
    if (cached != null) {
      return cached == NO_SHAPE ? null : cached;
    }
    Shape result =
        shapeCache.computeIfAbsent(
            key,
            ignored -> {
              Shape computed = computeShape(type, config);
              return computed == null ? NO_SHAPE : computed;
            });
    return result == NO_SHAPE ? null : result;
  }

  /**
   * Translation start for one top-level synthetic-map key: the member's resource-relative location
   * prefix plus its declared type for nested walking.
   */
  record ConstructionStart(MappingLocation location, JavaType declaredType) {}

  /**
   * Translates a failed bean-construction Jackson path into a resource-relative mapping location
   * (ADR-014). The path's first name selects the member's start through {@code startsByLogicalName}
   * (Jackson logical name to wire prefix); deeper names are walked through resolved shape metadata,
   * each matching member contributing its escaped wire-name segment. Walking stops at the first
   * name that is not a shape member, so Jackson-internal names below an atomic member are never
   * leaked into the location. The internal presence-marker {@code value} member between two
   * presence-aware shape levels is skipped.
   *
   * <p>{@code walkPlainShapes} extends walking beyond presence-aware shapes: the flat DTO binder
   * uses it to follow ordinary structured attribute types, while the typed PATCH DTO path keeps the
   * stricter presence-aware-only walk. Returns {@code null} when the path is empty or its first
   * name matches no mapped member — an absent location per the mapping-location contract, never a
   * Jackson logical property name.
   */
  @Nullable MappingLocation translateConstructionPath(
      List<String> names,
      Map<String, ConstructionStart> startsByLogicalName,
      boolean walkPlainShapes) {
    if (names.isEmpty()) {
      return null;
    }
    ConstructionStart start = startsByLogicalName.get(names.getFirst());
    if (start == null) {
      return null;
    }
    MappingLocation pointer = start.location();
    JavaType current = start.declaredType();
    boolean walking = true;
    for (int i = 1; i < names.size() && walking; i++) {
      String name = names.get(i);
      Shape shape = walkShape(current, walkPlainShapes);
      if (shape == null) {
        walking = false;
      } else {
        Member member = shape.memberByWire(name);
        if (member != null) {
          pointer = pointer.append(member.wireName());
          current = member.type();
        } else if (!shape.presenceAware() || !"value".equals(name)) {
          walking = false;
        }
      }
    }
    return pointer;
  }

  private @Nullable Shape walkShape(JavaType type, boolean includePlainShapes) {
    Shape shape = shapeOf(unwrapOptional(unwrapPatchPresence(type)));
    if (shape == null) {
      return null;
    }
    return includePlainShapes || shape.presenceAware() ? shape : null;
  }

  private @Nullable Shape computeShape(JavaType type, DeserializationConfig config) {
    DeserializationContext context = mapper._deserializationContext();
    ValueDeserializer<?> deserializer = context.findRootValueDeserializer(type);
    if (!(deserializer instanceof BeanDeserializerBase)) {
      return null;
    }
    ClassIntrospector introspector = config.classIntrospectorInstance();
    AnnotatedClass annotatedClass = introspector.introspectClassAnnotations(type);
    BeanDescription beanDescription =
        introspector.introspectForDeserialization(type, annotatedClass);
    List<Member> members = new ArrayList<>();
    boolean hasPresenceAttempt = false;
    boolean allExactlyPresence = true;
    for (BeanPropertyDefinition definition : beanDescription.findProperties()) {
      if (definition.getMutator() == null && !definition.hasConstructorParameter()) {
        continue;
      }
      JavaType memberType = definition.getPrimaryType();
      if (isPresenceAttempt(memberType)) {
        hasPresenceAttempt = true;
      }
      if (!isPatchPresence(memberType)) {
        allExactlyPresence = false;
      }
      members.add(
          new Member(
              definition.getInternalName(),
              definition.getFullName().getSimpleName(),
              memberType,
              definition.getMutator(),
              definition.getAccessor()));
    }
    boolean presenceAware = hasPresenceAttempt && allExactlyPresence && !members.isEmpty();
    return new Shape(
        type, List.copyOf(members), presenceAware, hasPresenceAttempt && !allExactlyPresence);
  }

  private static int configHash(DeserializationConfig config) {
    int result =
        config.getPropertyNamingStrategy() != null
            ? config.getPropertyNamingStrategy().hashCode()
            : 0;
    result = 31 * result + config.getDefaultVisibilityChecker().hashCode();
    return result;
  }

  // ============================== HELPERS ==============================

  private static boolean isPatchPresence(JavaType type) {
    return type.getRawClass() == PatchPresence.class && type.containedTypeCount() == 1;
  }

  private static boolean isPresenceAttempt(JavaType type) {
    Class<?> raw = type.getRawClass();
    return raw == PatchPresence.class
        || raw == PatchPresence.Present.class
        || raw == PatchPresence.Omitted.class;
  }

  private static boolean isOptional(JavaType type) {
    return type.getRawClass() == Optional.class && type.containedTypeCount() == 1;
  }

  private static JavaType unwrapPatchPresence(JavaType type) {
    return isPatchPresence(type) ? type.containedType(0) : type;
  }

  private static JavaType unwrapOptional(JavaType type) {
    while (isOptional(type)) {
      type = type.containedType(0);
    }
    return type;
  }

  private static JavaType unwrapLowLevel(JavaType type) {
    return unwrapOptional(unwrapPatchPresence(type));
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

  private static JsonApiMappingException unknownPatchMember(
      Class<?> rawType, MappingLocation path, String name) {
    return new JsonApiMappingException(
        MappingDiagnostic.UNKNOWN_PATCH_MEMBER,
        rawType,
        path,
        "Unknown supplied nested member '" + name + "' at '" + path + "'");
  }

  private static JsonApiMappingException invalidPatchPropertyType(
      Class<?> rawType, MappingLocation path, String detail) {
    return new JsonApiMappingException(
        MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE,
        rawType,
        path,
        "Invalid nested PATCH property type at '" + path + "': " + detail);
  }

  private static JsonApiMappingException unsupported(
      Class<?> rawType, MappingLocation path, String message) {
    return new JsonApiMappingException(
        MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE, rawType, path, message);
  }

  /**
   * One visible, bindable member of a structured shape. {@code deserializationMember} is the
   * property's deserialization-side member ({@code BeanPropertyDefinition.getMutator()}: creator
   * parameter, then setter, then field) and {@code serializationMember} its serialization-side
   * member ({@code BeanPropertyDefinition.getAccessor()}: getter, then field). Jackson may place
   * deserialization and serialization annotations on different accessors of the same property, so
   * both are carried explicitly rather than a single ambiguous accessor.
   */
  record Member(
      String internalName,
      String wireName,
      JavaType type,
      @Nullable AnnotatedMember deserializationMember,
      @Nullable AnnotatedMember serializationMember) {}

  /** Resolved structured shape: visible members plus typed-mode classification. */
  record Shape(
      @Nullable JavaType type, List<Member> members, boolean presenceAware, boolean mixed) {

    @Nullable Member memberByWire(String name) {
      for (Member member : members) {
        if (member.wireName().equals(name)) {
          return member;
        }
      }
      return null;
    }
  }

  private record CacheKey(JavaType type, int configHash) {}
}
