package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.mapping.internal.ReadConstructionStart;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.json.JsonMapper;

/**
 * Nested construction-path translation for ordinary flat reads (only the machinery the flat DTO
 * binder needs; recursive PATCH binding lives in the structured-value engine).
 *
 * <p>A failed whole-bean construction reports a Jackson failure path whose names are Jackson
 * logical property names. This class translates that path into a resource-relative {@link
 * MappingLocation}: the path's first name selects the member's shared start through the
 * backend-external-name to JSON:API location map, and deeper names are walked through the actual
 * configured bean deserializer's effective {@link SettableBeanProperty} values, each matching
 * member contributing its configured external name as the wire segment. The effective property is
 * the sole authority: a serialization-side primary type is never used as a fallback, so a
 * setter-only, creator-only, or type-refined member walks with its effective deserialization type.
 * Walking stops at the first name that is not a shape member, so Jackson-internal names below an
 * atomic or custom-deserializer member are never leaked into the location.
 *
 * <p>Shape resolution is cached per complete {@link JavaType} on the isolated mapper instance;
 * deserialization configuration cannot change after the mapper is built.
 */
final class FlatConstructionPaths {

  private static final Shape NO_SHAPE = new Shape(List.of());

  private final JsonMapper mapper;
  private final Map<JavaType, Shape> shapeCache = new ConcurrentHashMap<>();

  FlatConstructionPaths(JsonMapper mapper) {
    this.mapper = mapper;
  }

  /**
   * Translates a failed bean-construction Jackson path into a resource-relative mapping location.
   * Returns {@code null} when the path is empty or its first name matches no bindable mapped member
   * — an absent location per the mapping-location contract, never a Jackson logical property name.
   */
  @Nullable MappingLocation translateConstructionPath(
      List<String> names,
      Map<String, ReadConstructionStart<ReadMappingProperty>> startsByExternalName) {
    if (names.isEmpty()) {
      return null;
    }
    ReadConstructionStart<ReadMappingProperty> start = startsByExternalName.get(names.getFirst());
    if (start == null) {
      return null;
    }
    MappingLocation pointer = start.location();
    JavaType current = start.property().token().type();
    boolean walking = true;
    for (int i = 1; i < names.size() && walking; i++) {
      String name = names.get(i);
      Shape shape = shapeOf(unwrapOptional(current));
      if (shape == null) {
        walking = false;
      } else {
        Shape.Member member = shape.memberByName(name);
        if (member != null) {
          pointer = pointer.append(member.wireName());
          current = member.type();
        } else {
          walking = false;
        }
      }
    }
    return pointer;
  }

  private @Nullable Shape shapeOf(JavaType type) {
    Shape cached = shapeCache.get(type);
    if (cached != null) {
      return cached == NO_SHAPE ? null : cached;
    }
    Shape result =
        shapeCache.computeIfAbsent(
            type,
            ignored -> {
              Shape computed = computeShape(type);
              return computed == null ? NO_SHAPE : computed;
            });
    return result == NO_SHAPE ? null : result;
  }

  /**
   * Resolves the walkable bean shape of {@code type}, or {@code null} for atomic types (scalars,
   * containers, custom/scalar deserializers). Members are resolved from the actual configured bean
   * deserializer's effective properties, honoring injection-only exclusion and active-view
   * visibility, and keyed by each property's configured Jackson external name.
   */
  private @Nullable Shape computeShape(JavaType type) {
    ValueDeserializer<?> deserializer =
        mapper._deserializationContext().findRootValueDeserializer(type);
    if (!(deserializer instanceof BeanDeserializerBase bean)) {
      return null;
    }
    Class<?> activeView = mapper.deserializationConfig().getActiveView();
    List<Shape.Member> members = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    addMembers(bean.properties(), members, seen, activeView);
    addMembers(bean.creatorProperties(), members, seen, activeView);
    return new Shape(List.copyOf(members));
  }

  private static void addMembers(
      Iterator<SettableBeanProperty> properties,
      List<Shape.Member> members,
      Set<String> seen,
      @Nullable Class<?> activeView) {
    while (properties.hasNext()) {
      SettableBeanProperty property = properties.next();
      if (property.isInjectionOnly()) {
        continue;
      }
      if (activeView != null && !property.visibleInView(activeView)) {
        continue;
      }
      if (seen.add(property.getName())) {
        members.add(new Shape.Member(property.getName(), property.getType()));
      }
    }
  }

  private static boolean isOptional(JavaType type) {
    return type.getRawClass() == Optional.class && type.containedTypeCount() == 1;
  }

  private static JavaType unwrapOptional(JavaType type) {
    while (isOptional(type)) {
      type = type.containedType(0);
    }
    return type;
  }

  /** Resolved walkable bean shape: effective deserialization members keyed by external name. */
  private record Shape(List<Member> members) {

    @Nullable Member memberByName(String name) {
      for (Member member : members) {
        if (member.wireName().equals(name)) {
          return member;
        }
      }
      return null;
    }

    /**
     * One walkable member. The wire name is the configured Jackson external name used as the {@code
     * convertValue} map key; the type is the effective deserialization type of the configured bean
     * deserializer's property for that name.
     */
    record Member(String wireName, JavaType type) {}
  }
}
