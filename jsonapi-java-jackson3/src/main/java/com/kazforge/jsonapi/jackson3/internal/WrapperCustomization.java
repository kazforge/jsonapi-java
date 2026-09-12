package com.kazforge.jsonapi.jackson3.internal;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.json.JsonMapper;

/**
 * Detects wrapper-level Jackson serialization/deserialization customization on a member.
 *
 * <p>Shared by the typed PATCH DTO declaration validation and the structured-value engine so the
 * two cannot silently drift on what counts as wrapper-level {@code @JsonDeserialize} /
 * {@code @JsonSerialize} customization that must be rejected on presence-aware members.
 */
final class WrapperCustomization {

  private WrapperCustomization() {}

  /**
   * Checks wrapper-level serialization and deserialization customization on the property, on both
   * its serialization-side and deserialization-side members. Jackson may surface a property-scoped
   * annotation through the accessor (getter / field), the mutator (creator parameter / setter /
   * field), or both depending on the class shape, so both members are inspected for both directions
   * to avoid missing setter-, creator-, field-, or getter-placed {@code @JsonDeserialize} and
   * {@code @JsonSerialize}.
   *
   * <p>{@code declaredType} must be the property's already-resolved {@link JavaType} (for example
   * {@code BeanPropertyDefinition.getPrimaryType()} or the structured engine's resolved member
   * type), never derived from {@link AnnotatedMember#getType()}: for setter methods that returns
   * the method return type ({@code void}) instead of the setter parameter type, which makes
   * refinement-based checks (as / contentAs / keyAs) incorrect.
   */
  static boolean has(
      JsonMapper mapper,
      JavaType declaredType,
      @Nullable AnnotatedMember serializationMember,
      @Nullable AnnotatedMember deserializationMember) {
    return hasSerialization(mapper, declaredType, serializationMember)
        || hasSerialization(mapper, declaredType, deserializationMember)
        || hasDeserialization(mapper, declaredType, serializationMember)
        || hasDeserialization(mapper, declaredType, deserializationMember);
  }

  /**
   * True when the member carries any deserialization customization that would win over plain
   * binding.
   */
  static boolean hasDeserialization(
      JsonMapper mapper, JavaType declaredType, @Nullable AnnotatedMember member) {
    if (member == null) {
      return false;
    }
    DeserializationConfig config = mapper.deserializationConfig();
    var introspector = config.getAnnotationIntrospector();
    return introspector.findDeserializer(config, member) != null
        || introspector.findKeyDeserializer(config, member) != null
        || introspector.findContentDeserializer(config, member) != null
        || introspector.findDeserializationConverter(config, member) != null
        || introspector.findDeserializationContentConverter(config, member) != null
        || typeRefined(
            introspector.refineDeserializationType(config, member, declaredType), declaredType);
  }

  private static boolean hasSerialization(
      JsonMapper mapper, JavaType declaredType, @Nullable AnnotatedMember member) {
    if (member == null) {
      return false;
    }
    SerializationConfig config = mapper.serializationConfig();
    var introspector = config.getAnnotationIntrospector();
    return introspector.findSerializer(config, member) != null
        || introspector.findKeySerializer(config, member) != null
        || introspector.findContentSerializer(config, member) != null
        || introspector.findNullSerializer(config, member) != null
        || introspector.findSerializationConverter(config, member) != null
        || introspector.findSerializationContentConverter(config, member) != null
        || introspector.findSerializationTyping(config, member) != null
        || typeRefined(
            introspector.refineSerializationType(config, member, declaredType), declaredType);
  }

  private static boolean typeRefined(@Nullable JavaType refined, JavaType declared) {
    return refined != null && !refined.equals(declared);
  }
}
