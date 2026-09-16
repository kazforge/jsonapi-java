package com.kazforge.jsonapi.jackson.diagnostic;

import java.io.Serial;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Domain mapping, binding, registry, decoration, or representation failure.
 *
 * <p>Every exception carries a stable {@link MappingDiagnostic} code, an optional {@link
 * #resourceClass()}, and an optional {@link #location()} over JSON:API wire names. Adapter-produced
 * locations follow one coordinate contract:
 *
 * <ul>
 *   <li>Direct resource operations use resource-relative pointers such as {@code
 *       /attributes/headline} or {@code /relationships/author/data}. Typed-envelope readers prepend
 *       {@code /data}, {@code /data/<index>}, or {@code /included/<index>} so an escaping failure
 *       is document-relative.
 *   <li>Each segment is escaped independently per RFC 6901. Configured Jackson external names,
 *       rather than logical Java property names, appear in the pointer.
 *   <li>A failure without a meaningful member location carries {@code null}; absence is never
 *       encoded as {@code ""}, {@code /}, or a class name.
 * </ul>
 *
 * <p>{@link #location()} is the canonical structural accessor; {@link #propertyPath()} is its
 * string view. Document decoding and core document validation failures use {@link
 * JsonApiDocumentReadException}, not this family.
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
