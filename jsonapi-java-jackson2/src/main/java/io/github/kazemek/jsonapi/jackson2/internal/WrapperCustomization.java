package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.jspecify.annotations.Nullable;

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
    MapperConfig<?> config = mapper.getDeserializationConfig();
    var introspector = config.getAnnotationIntrospector();
    return introspector.findDeserializer(member) != null
        || introspector.findKeyDeserializer(member) != null
        || introspector.findContentDeserializer(member) != null
        || introspector.findDeserializationConverter(member) != null
        || introspector.findDeserializationContentConverter(member) != null
        || typeRefined(refineDeserialization(config, member, declaredType), declaredType);
  }

  private static boolean hasSerialization(
      JsonMapper mapper, JavaType declaredType, @Nullable AnnotatedMember member) {
    if (member == null) {
      return false;
    }
    MapperConfig<?> config = mapper.getSerializationConfig();
    var introspector = config.getAnnotationIntrospector();
    return introspector.findSerializer(member) != null
        || introspector.findKeySerializer(member) != null
        || introspector.findContentSerializer(member) != null
        || introspector.findNullSerializer(member) != null
        || introspector.findSerializationConverter(member) != null
        || introspector.findSerializationContentConverter(member) != null
        || introspector.findSerializationTyping(member) != null
        || typeRefined(refineSerialization(config, member, declaredType), declaredType);
  }

  private static @Nullable JavaType refineDeserialization(
      MapperConfig<?> config, AnnotatedMember member, JavaType declaredType) {
    try {
      return config
          .getAnnotationIntrospector()
          .refineDeserializationType(config, member, declaredType);
    } catch (JsonMappingException e) {
      throw new IllegalStateException(
          "Failed to inspect deserialization type refinement for " + member, e);
    }
  }

  private static @Nullable JavaType refineSerialization(
      MapperConfig<?> config, AnnotatedMember member, JavaType declaredType) {
    try {
      return config
          .getAnnotationIntrospector()
          .refineSerializationType(config, member, declaredType);
    } catch (JsonMappingException e) {
      throw new IllegalStateException(
          "Failed to inspect serialization type refinement for " + member, e);
    }
  }

  private static boolean typeRefined(@Nullable JavaType refined, JavaType declared) {
    return refined != null && !refined.equals(declared);
  }
}
