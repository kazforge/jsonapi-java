package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Shared bean construction from a synthetic property map with a single {@link
 * JsonMapper#convertValue(Object, JavaType)}, plus stable creator/coercion failure classification
 * used by the flat DTO binder.
 *
 * <p>The binder supplies a {@link FailurePathTranslator} so deep Jackson construction-failure paths
 * are translated into resource-relative {@link MappingLocation} pointers through the resource
 * mapping. Translators never emit Jackson logical property names as locations: unmappable paths
 * translate to an absent location.
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
      @Nullable FailurePathTranslator translator,
      Set<String> creatorPropertyNames) {
    try {
      return mapper.convertValue(properties, targetType);
    } catch (RuntimeException e) {
      Throwable failure = jacksonFailure(e);
      MappingDiagnostic diagnostic =
          isCreatorInputFailure(failure, properties, creatorPropertyNames)
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
   * Classifies bulk {@code convertValue} failures as creator/instantiation input problems.
   *
   * <p>Jackson 2.22 reports creator/instantiation failures (including throwing creators) as {@link
   * ValueInstantiationException}, and missing creator input as {@link MismatchedInputException}
   * whose first path names an effective creator property absent from the synthetic input.
   * Classification is deliberately message-independent: a supplied value whose shape does not match
   * the target can surface through the same {@link MismatchedInputException} class, but such a
   * value is present in the synthetic input, so absence distinguishes missing creator input from a
   * supplied-value failure. All other coercion, type, or property failures map to {@link
   * MappingDiagnostic#UNSUPPORTED_ATTRIBUTE_VALUE}.
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
    if (failure instanceof com.fasterxml.jackson.databind.JsonMappingException mapping) {
      List<com.fasterxml.jackson.databind.JsonMappingException.Reference> path = mapping.getPath();
      if (path != null) {
        List<String> names = new ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonMappingException.Reference reference : path) {
          String name = reference.getFieldName();
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
    return first.equals(property.jacksonName())
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
