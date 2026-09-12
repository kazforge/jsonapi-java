package com.kazforge.jsonapi.jackson.diagnostic;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.StringJoiner;
import org.jspecify.annotations.Nullable;

/**
 * A validated mapping-diagnostic location: a JSON Pointer (RFC 6901) that is absent ({@code null})
 * on the owning {@link JsonApiMappingException} when no member location applies. This is the
 * structural building block behind the mapping-location contract; producers never assemble
 * diagnostic pointers by raw string concatenation of unconstrained input.
 *
 * <p><strong>Coordinate contract.</strong> Mapping producers address members of one resource object
 * with resource-relative pointers such as {@code /type}, {@code /id}, {@code /lid}, {@code
 * /attributes/headline}, {@code /relationships/author/data}, {@code /meta}, {@code
 * /relationships/author/meta}, {@code /relationships/author/data/meta}, and {@code
 * /relationships/comments/data/0/meta}. Typed-envelope composition prepends document-relative
 * prefixes ({@code /data}, {@code /data/<index>}, {@code /included/<index>}) through {@link
 * #append(MappingLocation)}, which cannot produce malformed syntax because both operands are built
 * from validated, individually escaped, non-empty segments. Failures without a meaningful member
 * location are represented as an absent location, never as {@code ""} or {@code /}.
 *
 * <p><strong>Escaping.</strong> Segment-taking factories escape each segment independently per RFC
 * 6901 ({@code ~} to {@code ~0}, then {@code /} to {@code ~1}); the pointer as a whole is never
 * escaped as one string. {@link #parse(String)} accepts only pointers that already satisfy this
 * contract: leading {@code /}, no empty segments, and {@code ~} only inside valid {@code ~0} /
 * {@code ~1} escape sequences. Instances are immutable and safe for concurrent use.
 */
public final class MappingLocation implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private final String pointer;

  private MappingLocation(String pointer) {
    this.pointer = pointer;
  }

  /**
   * Creates a location from one or more raw segments, escaping each segment per RFC 6901 before
   * composition. For example {@code of("attributes", "external/name")} yields {@code
   * /attributes/external~1name}.
   *
   * @throws NullPointerException when any segment is null
   * @throws IllegalArgumentException when any segment is empty
   */
  public static MappingLocation of(String firstSegment, String... moreSegments) {
    StringJoiner joiner =
        new StringJoiner("/", "/", "").add(escape(requireSegment(firstSegment, "firstSegment")));
    for (String segment : moreSegments) {
      joiner.add(escape(requireSegment(segment, "segment")));
    }
    return new MappingLocation(joiner.toString());
  }

  /**
   * Parses an already-escaped pointer string.
   *
   * @throws IllegalArgumentException when the pointer is null, does not start with {@code /},
   *     contains an empty segment ({@code //}), or contains {@code ~} outside a valid {@code ~0} /
   *     {@code ~1} escape sequence
   */
  public static MappingLocation parse(String pointer) {
    validate(pointer);
    return new MappingLocation(pointer);
  }

  private static void validate(@Nullable String pointer) {
    if (pointer == null || !pointer.startsWith("/")) {
      throw new IllegalArgumentException(
          "Mapping location must be an absolute JSON Pointer: " + pointer);
    }
    int index = 1;
    while (index <= pointer.length()) {
      int nextSlash = pointer.indexOf('/', index);
      int end = nextSlash == -1 ? pointer.length() : nextSlash;
      if (end == index) {
        throw new IllegalArgumentException("Mapping location has an empty segment: " + pointer);
      }
      validateSegmentEscapes(pointer, index, end);
      index = end + 1;
    }
  }

  private static void validateSegmentEscapes(String pointer, int from, int end) {
    int index = from;
    while (index < end) {
      char current = pointer.charAt(index);
      if (current == '~') {
        boolean validEscape =
            index + 1 < end
                && (pointer.charAt(index + 1) == '0' || pointer.charAt(index + 1) == '1');
        if (!validEscape) {
          throw new IllegalArgumentException(
              "Mapping location has an invalid '~' escape sequence: " + pointer);
        }
        index += 2;
      } else {
        index++;
      }
    }
  }

  /**
   * Escapes one raw segment per RFC 6901: {@code ~} becomes {@code ~0}, {@code /} becomes {@code
   * ~1}.
   */
  static String escape(String segment) {
    String tildeEscaped = segment.replace("~", "~0");
    return tildeEscaped.replace("/", "~1");
  }

  private static String requireSegment(@Nullable String segment, String name) {
    Objects.requireNonNull(segment, name);
    if (segment.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be empty");
    }
    return segment;
  }

  /**
   * Returns a location addressing {@code segment} inside this location, escaping the segment. The
   * structural equivalent of appending one member without ever concatenating unvalidated input.
   *
   * @throws NullPointerException when the segment is null
   * @throws IllegalArgumentException when the segment is empty
   */
  public MappingLocation append(String segment) {
    return new MappingLocation(pointer + "/" + escape(requireSegment(segment, "segment")));
  }

  /**
   * Structurally joins a relative location under this location: a document prefix such as {@code
   * /data/2} plus a resource-relative suffix such as {@code /attributes/title} yields {@code
   * /data/2/attributes/title}. Both operands are already validated, so the join can never produce
   * malformed pointer syntax such as {@code /datatitle}.
   */
  public MappingLocation append(MappingLocation relative) {
    Objects.requireNonNull(relative, "relative");
    return new MappingLocation(pointer + relative.pointer);
  }

  /** Returns the canonical pointer text; never empty, always starting with {@code /}. */
  public String pointer() {
    return pointer;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof MappingLocation other && pointer.equals(other.pointer);
  }

  @Override
  public int hashCode() {
    return pointer.hashCode();
  }

  @Override
  public String toString() {
    return pointer;
  }
}
