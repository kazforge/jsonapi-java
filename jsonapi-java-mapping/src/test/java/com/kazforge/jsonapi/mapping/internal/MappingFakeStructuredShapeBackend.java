package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.diagnostic.MappingLocation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Mapping-local test double for {@link StructuredShapeBackend} over string type tokens.
 *
 * <p>Tokens encode the native facts the shared engine consumes: {@code bean:<name>} for a
 * registered shape, {@code presence:<inner>} for an exact {@code PatchPresence}, {@code
 * present:<inner>} / {@code omitted:<inner>} for presence attempts, {@code optional:<inner>} for an
 * {@link java.util.Optional}, {@code int} for a primitive, and any other token for an atomic type.
 * Test code registers shapes and per-member facts directly; no traversal semantics are simulated.
 */
@NullMarked
final class MappingFakeStructuredShapeBackend implements StructuredShapeBackend<String> {

  final Map<String, StructuredShape<String>> shapes = new LinkedHashMap<>();
  final Map<String, @Nullable Object> convertedValues = new LinkedHashMap<>();
  final List<AtomicCall> atomicCalls = new ArrayList<>();

  record AtomicCall(
      String beanType,
      String declaredType,
      String targetType,
      String wireName,
      @Nullable Object wire) {}

  static StructuredShape.Member<String> member(String wireName, String declaredType) {
    return member(wireName, declaredType, false, false);
  }

  static StructuredShape.Member<String> member(
      String wireName, String declaredType, boolean wrapperCustomization) {
    return member(wireName, declaredType, wrapperCustomization, false);
  }

  static StructuredShape.Member<String> member(
      String wireName,
      String declaredType,
      boolean wrapperCustomization,
      boolean deserializationCustomization) {
    return new StructuredShape.Member<>(
        wireName,
        wireName,
        declaredType,
        declaredType.startsWith("presence:"),
        declaredType.startsWith("presence:")
            || declaredType.startsWith("present:")
            || declaredType.startsWith("omitted:"),
        wrapperCustomization,
        deserializationCustomization);
  }

  @SafeVarargs
  final void defineShape(String beanToken, StructuredShape.Member<String>... members) {
    shapes.put(beanToken, new StructuredShape<>(List.of(members)));
  }

  void convertedValue(String wireName, @Nullable Object value) {
    convertedValues.put(wireName, value);
  }

  @Override
  public @Nullable StructuredShape<String> shapeOf(String type) {
    return shapes.get(type);
  }

  @Override
  public boolean isPatchPresence(String type) {
    return type.startsWith("presence:");
  }

  @Override
  public String patchPresenceInner(String type) {
    return type.substring("presence:".length());
  }

  @Override
  public boolean isOptional(String type) {
    return type.startsWith("optional:");
  }

  @Override
  public String optionalInner(String type) {
    return type.substring("optional:".length());
  }

  @Override
  public boolean isPrimitive(String type) {
    return "int".equals(type);
  }

  @Override
  public String typeName(String type) {
    return type;
  }

  @Override
  public @Nullable Object convertAtomic(
      String beanType,
      String declaredType,
      String targetType,
      String wireName,
      @Nullable Object wire,
      MappingLocation pointer,
      Class<?> rawType) {
    atomicCalls.add(new AtomicCall(beanType, declaredType, targetType, wireName, wire));
    return convertedValues.getOrDefault(wireName, wire);
  }
}
