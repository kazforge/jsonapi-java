package com.kazforge.jsonapi.jackson.diagnostic;

import java.io.Serial;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Thrown when the domain-to-resource mapping layer encounters a structural or value problem.
 *
 * <p>Every exception carries a stable {@link MappingDiagnostic} code, an optional {@link
 * #resourceClass()}, and an optional mapping {@link #location()}. The location is the single
 * documented coordinate contract for mapping diagnostics:
 *
 * <ul>
 *   <li>When present, the location is a valid JSON Pointer (RFC 6901) whose segments are
 *       individually escaped ({@code ~} to {@code ~0}, {@code /} to {@code ~1}). Producers that map
 *       one resource object emit <em>resource-relative</em> pointers over JSON:API member names —
 *       {@code /type}, {@code /id}, {@code /lid}, {@code /attributes/headline}, {@code
 *       /relationships/author/data}, {@code /meta}, {@code /relationships/author/meta}, {@code
 *       /relationships/author/data/meta}, {@code /relationships/comments/data/0/meta}. Failures
 *       that have no meaningful member location (missing annotations, registry conflicts,
 *       include-path or fieldset specification errors) carry an <em>absent</em> location; the
 *       identifying names remain in the message. Absence is never encoded as {@code ""}, {@code /},
 *       or a class name.
 *   <li>Typed-envelope composition in the major-specific domain document readers structurally joins
 *       the resource-relative location under a document-relative prefix ({@code /data}, {@code
 *       /data/<index>}, {@code /included/<index>}), so failures escaping that boundary are
 *       document-relative. Document-level conversions without a resource context (such as envelope
 *       {@code metaAs}) report document-relative pointers directly.
 *   <li>The location addresses wire (JSON:API member) names. Java/Jackson logical property names
 *       are translated through the resource mapping before they appear in a location; a logical
 *       name is never silently reinterpreted as pointer syntax.
 * </ul>
 *
 * <p>{@link #location()} is the canonical structural accessor; {@link #propertyPath()} is its
 * plain-string view. Locations are built through {@link MappingLocation}, so malformed or
 * logical-name "pointers" cannot enter the contract.
 */
public final class JsonApiMappingException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final MappingDiagnostic diagnostic;
  private final @Nullable Class<?> resourceClass;
  private final @Nullable MappingLocation location;

  public JsonApiMappingException(
      MappingDiagnostic diagnostic,
      @Nullable Class<?> resourceClass,
      @Nullable MappingLocation location) {
    this(diagnostic, resourceClass, location, diagnostic.name(), null);
  }

  public JsonApiMappingException(
      MappingDiagnostic diagnostic,
      @Nullable Class<?> resourceClass,
      @Nullable MappingLocation location,
      String message) {
    this(diagnostic, resourceClass, location, message, null);
  }

  public JsonApiMappingException(
      MappingDiagnostic diagnostic,
      @Nullable Class<?> resourceClass,
      @Nullable MappingLocation location,
      String message,
      @Nullable Throwable cause) {
    super(message, cause);
    this.diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
    this.resourceClass = resourceClass;
    this.location = location;
  }

  /** Convenience constructor for failures with neither a resource class nor a member location. */
  public JsonApiMappingException(MappingDiagnostic diagnostic, String message) {
    this(diagnostic, null, null, message, null);
  }

  /**
   * Factory for failures that explicitly carry no member location (for example missing annotations
   * or registry conflicts); identifying names belong in the message per the class contract.
   */
  public static JsonApiMappingException withoutLocation(
      MappingDiagnostic diagnostic, @Nullable Class<?> resourceClass, String message) {
    return new JsonApiMappingException(diagnostic, resourceClass, null, message);
  }

  public MappingDiagnostic diagnostic() {
    return diagnostic;
  }

  public @Nullable Class<?> resourceClass() {
    return resourceClass;
  }

  /**
   * The mapping location: a valid resource-relative or document-relative JSON Pointer when the
   * failure has a meaningful member location, otherwise {@code null}. See the class documentation
   * for the full coordinate contract.
   */
  public @Nullable MappingLocation location() {
    return location;
  }

  /** The pointer text of {@link #location()} when present, otherwise {@code null}. */
  public @Nullable String propertyPath() {
    return location == null ? null : location.pointer();
  }
}
