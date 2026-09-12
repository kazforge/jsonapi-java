package com.kazforge.jsonapi.jackson.mapping;

import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic;
import org.jspecify.annotations.Nullable;

/**
 * Converts a domain identifier value to its JSON:API string representation, and back.
 *
 * <p>One conversion authority serves both identity roles: the {@code id} role mapped by
 * {@code @JsonApiId} and the {@code lid} role mapped by {@code @JsonApiLocalId}. Their Java scalar
 * conversion semantics are equivalent; only the JSON:API member they land on differs.
 *
 * <p>Write side: implementations receive the raw identifier value from the mapped identity property
 * and return the string used as {@code "id"} or {@code "lid"} in the resource object. Return {@code
 * null} to signal a missing identifier, which causes a {@link JsonApiMappingException} with {@link
 * MappingDiagnostic#MISSING_IDENTIFIER} at that role's wire location.
 *
 * <p>Read side: {@link #parse(String)} inverts {@link #convert(Object)}. The flat DTO binder passes
 * the wire identifier string to {@link #parse(String)} and coerces the returned value to the
 * identity property's Java type. The default implementation returns the wire string unchanged;
 * custom converters that alter the wire form must override {@link #parse(String)} to invert that
 * form.
 *
 * <p>Use {@link #defaults()} for the built-in {@code toString()} conversion.
 */
@FunctionalInterface
public interface IdentifierConverter {

  /**
   * Converts a raw identifier value to its string form. A {@code null} return signals a missing
   * identifier.
   */
  @Nullable String convert(@Nullable Object identifierValue);

  /**
   * Parses a JSON:API identifier string back into a value for the identifier property.
   *
   * <p>Called only when the resource object carries an identifier ({@code id} or {@code lid}). The
   * returned value is coerced to the identifier property's Java type by the binder. A {@code null}
   * return fails the bind with {@link MappingDiagnostic#IDENTIFIER_CONVERSION_FAILED}.
   */
  default @Nullable Object parse(@Nullable String wireIdentifier) {
    return wireIdentifier;
  }

  /** Returns the built-in converter that delegates to {@link Object#toString()}. */
  static IdentifierConverter defaults() {
    return identifierValue -> identifierValue != null ? identifierValue.toString() : null;
  }
}
