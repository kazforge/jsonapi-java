package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kazforge.jsonapi.jackson.diagnostic.MappingLocation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Nested construction-path translation for ordinary flat reads (only the machinery the flat DTO
 * binder needs; the structured PATCH engine of this capability family is not part of Jackson 2
 * yet).
 *
 * <p>A failed whole-bean construction reports a Jackson failure path whose names are Jackson
 * logical property names. This class translates that path into a resource-relative {@link
 * MappingLocation} through the read mapping: the path's first name selects the member's start
 * through {@code startsByJacksonName} (Jackson external name to wire prefix), and deeper names are
 * walked through resolved bean-shape metadata, each matching member contributing its escaped
 * wire-name segment. Walking stops at the first name that is not a shape member, so
 * Jackson-internal names below an atomic member are never leaked into the location.
 *
 * <p>Shape resolution is cached per complete {@link JavaType} on the isolated mapper instance;
 * deserialization configuration cannot change after the mapper is built.
 */
final class FlatConstructionPaths {

  /**
   * Translation start for one top-level synthetic-map key: the member's resource-relative location
   * prefix plus its declared type for nested walking.
   */
  record ConstructionStart(MappingLocation location, JavaType declaredType) {}

  private static final Shape NO_SHAPE = new Shape(List.of());

  private final JsonMapper mapper;
  private final Map<JavaType, Shape> shapeCache = new ConcurrentHashMap<>();

  FlatConstructionPaths(JsonMapper mapper) {
    this.mapper = mapper;
  }

  /**
   * Translates a failed bean-construction Jackson path into a resource-relative mapping location.
   * Returns {@code null} when the path is empty or its first name matches no mapped member — an
   * absent location per the mapping-location contract, never a Jackson logical property name.
   */
  @Nullable MappingLocation translateConstructionPath(
      List<String> names, Map<String, ConstructionStart> startsByJacksonName) {
    if (names.isEmpty()) {
      return null;
    }
    ConstructionStart start = startsByJacksonName.get(names.getFirst());
    if (start == null) {
      return null;
    }
    MappingLocation pointer = start.location();
    JavaType current = start.declaredType();
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
   * deserializer side: only properties with a deserialization mutator or a constructor parameter
   * participate, keyed by their configured Jackson external name.
   */
  private @Nullable Shape computeShape(JavaType type) {
    DefaultDeserializationContext context =
        ((DefaultDeserializationContext) mapper.getDeserializationContext())
            .createInstance(mapper.getDeserializationConfig(), null, null);
    JsonDeserializer<?> deserializer;
    try {
      deserializer = context.findRootValueDeserializer(type);
    } catch (com.fasterxml.jackson.databind.JsonMappingException e) {
      throw new IllegalStateException("Failed to resolve a deserializer for " + type, e);
    }
    if (!(deserializer instanceof BeanDeserializerBase)) {
      return null;
    }
    BeanDescription beanDescription =
        mapper
            .getDeserializationConfig()
            .getClassIntrospector()
            .forDeserialization(
                mapper.getDeserializationConfig(), type, mapper.getDeserializationConfig());
    List<Shape.Member> members = new java.util.ArrayList<>();
    for (BeanPropertyDefinition definition : beanDescription.findProperties()) {
      if (definition.getMutator() == null && !definition.hasConstructorParameter()) {
        continue;
      }
      members.add(new Shape.Member(definition.getName(), definition.getPrimaryType()));
    }
    return new Shape(List.copyOf(members));
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

  /** Resolved walkable bean shape: visible deserialization members keyed by external name. */
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
     * convertValue} map key; the type is the definition's primary type with any generics bound by
     * the surrounding declaration.
     */
    record Member(String wireName, JavaType type) {}
  }
}
