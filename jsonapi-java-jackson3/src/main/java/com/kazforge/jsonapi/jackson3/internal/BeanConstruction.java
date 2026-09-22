package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.ValueInstantiationException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared bean construction from a synthetic property map with a single {@link
 * JsonMapper#convertValue(Object, JavaType)}, plus stable creator/coercion failure classification
 * used by the flat DTO binder and the typed PATCH DTO binder.
 *
 * <p>Both binders supply a {@link FailurePathTranslator} so deep Jackson construction-failure paths
 * are translated into resource-relative {@link MappingLocation} pointers through the resource
 * mapping and, for nested structured members, through the resolved shape metadata. Translators
 * never emit Jackson logical property names as locations: unmappable paths translate to an absent
 * location.
 */
final class BeanConstruction {

  /**
   * Translates a failed bean-construction's Jackson path into a resource-relative mapping location,
   * or {@code null} when no member of the mapping matches the path.
   */
  @FunctionalInterface
  interface FailurePathTranslator {
    @Nullable MappingLocation translate(Throwable failure, Class<?> rawType);
  }

  private BeanConstruction() {}

  static Object convertBean(
      JsonMapper mapper,
      Map<String, @Nullable Object> properties,
      JavaType targetType,
      Class<?> rawType,
      @Nullable FailurePathTranslator translator) {
    return convertBean(mapper, properties, targetType, rawType, translator, null);
  }

  /**
   * Constructs the bean with a single {@code convertValue}, optionally classifying creator failures
   * against the effective creator property names. The typed PATCH DTO binder keeps the
   * message-based default classifier ({@code null} names); ordinary flat reads pass the read
   * mapping's effective creator external names so a missing-input mismatch is message-independent.
   */
  static Object convertBean(
      JsonMapper mapper,
      Map<String, @Nullable Object> properties,
      JavaType targetType,
      Class<?> rawType,
      @Nullable FailurePathTranslator translator,
      @Nullable Set<String> creatorPropertyNames) {
    try {
      return mapper.convertValue(properties, targetType);
    } catch (RuntimeException e) {
      Throwable failure = jacksonFailure(e);
      boolean creatorInputFailure =
          creatorPropertyNames == null
              ? isCreatorInputFailure(failure)
              : isCreatorInputFailure(failure, properties, creatorPropertyNames);
      MappingDiagnostic diagnostic =
          creatorInputFailure
              ? MappingDiagnostic.MISSING_CREATOR_INPUT
              : MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE;
      MappingLocation location = translator != null ? translator.translate(failure, rawType) : null;
      throw new JsonApiMappingException(
          diagnostic,
          rawType,
          location,
          "Failed to construct " + rawType.getName() + " from resource values",
          failure);
    }
  }

  /**
   * Message-independent creator classification against the effective creator property names. A
   * missing creator property surfaces as a {@link MismatchedInputException} whose first path names
   * an effective creator property absent from the synthetic input; a supplied value whose shape
   * does not match can surface through the same exception class, but such a value is present, so
   * absence distinguishes missing creator input from a supplied-value failure.
   * Creator/instantiation failures (including throwing creators) remain {@link
   * ValueInstantiationException} and are always creator input problems.
   */
  private static boolean isCreatorInputFailure(
      Throwable failure, Map<String, @Nullable Object> supplied, Set<String> creatorPropertyNames) {
    if (failure instanceof ValueInstantiationException) {
      return true;
    }
    if (failure instanceof MismatchedInputException) {
      List<String> names = pathNames(failure);
      if (!names.isEmpty()) {
        return !supplied.containsKey(names.getFirst())
            && creatorPropertyNames.contains(names.getFirst());
      }
      return false;
    }
    return false;
  }

  /**
   * Classifies bulk {@code convertValue} failures as creator/instantiation input problems.
   *
   * <p>Jackson 3.2 reports missing creator properties as {@link MismatchedInputException} with the
   * "Missing required creator property" message, and creator/instantiation failures (including
   * throwing creators) as {@link ValueInstantiationException}. Both mean the bean could not be
   * constructed from the supplied inputs, so both map to {@link
   * MappingDiagnostic#MISSING_CREATOR_INPUT}; all other coercion, type, or property failures map to
   * {@link MappingDiagnostic#UNSUPPORTED_ATTRIBUTE_VALUE}.
   */
  private static boolean isCreatorInputFailure(Throwable failure) {
    if (failure instanceof ValueInstantiationException) {
      return true;
    }
    if (failure instanceof MismatchedInputException mismatched) {
      String message = mismatched.getMessage();
      return message != null && message.contains("Missing required creator property");
    }
    return false;
  }

  private static Throwable jacksonFailure(Throwable failure) {
    Throwable current = failure;
    while (current != null) {
      if (current instanceof JacksonException) {
        return current;
      }
      current = current.getCause();
    }
    return failure;
  }

  /** The non-null property names of the Jackson failure path, outermost first. */
  static List<String> pathNames(Throwable failure) {
    if (failure instanceof JacksonException jackson) {
      List<JacksonException.Reference> path = jackson.getPath();
      if (path != null) {
        List<String> names = new ArrayList<>();
        for (JacksonException.Reference reference : path) {
          String name = reference.getPropertyName();
          if (name != null && !name.isEmpty()) {
            names.add(name);
          }
        }
        return names;
      }
    }
    return List.of();
  }

  /** Returns whether the outermost Jackson path member identifies the supplied mapping property. */
  static boolean pathStartsWithProperty(Throwable failure, MappingPropertyView property) {
    List<String> names = pathNames(failure);
    if (names.isEmpty()) {
      return false;
    }
    String first = names.getFirst();
    return first.equals(property.externalName())
        || first.equals(property.logicalName())
        || first.equals(property.definition().getFullName().getSimpleName());
  }

  /**
   * Returns whether a mapped bean-construction failure belongs to the supplied property. The
   * decision reads the raw Jackson failure path from the cause chain; the translated location on
   * the exception itself is only used as an exact-match signal so failures whose path was fully
   * translated to the property's own wire location still classify correctly.
   */
  static boolean isConstructionFailureForProperty(
      JsonApiMappingException failure,
      MappingPropertyView property,
      @Nullable MappingLocation propertyLocation) {
    Throwable constructionFailure = failure.getCause() == null ? failure : failure.getCause();
    if (!pathNames(constructionFailure).isEmpty()) {
      return pathStartsWithProperty(constructionFailure, property);
    }
    MappingLocation failureLocation = failure.location();
    return propertyLocation != null
        && failureLocation != null
        && propertyLocation.pointer().equals(failureLocation.pointer());
  }
}
