package com.kazforge.jsonapi.jackson.patch;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One requested change in a {@link PatchCommand}: a supplied mapped attribute, relationship, or
 * resource-side meta location. Identifier meta ({@code ResourceIdentifier.meta}) is not an
 * independent change variant; applications that need it opt into {@link
 * com.kazforge.jsonapi.jackson.mapping.RelationshipLinkage} and it participates only as part of
 * whole-linkage replacement on {@link RelationshipChange}.
 *
 * <p>Omitted members never appear. Explicit attribute JSON {@code null} is {@code value == null} on
 * a present {@link AttributeChange}. Relationship {@code NullLinkage} is Java {@code null} or empty
 * {@code Optional} as the Jackson adapter binder would produce. Resource and relationship meta
 * changes carry the converted atomic meta value, or a {@link StructuredPatch} where ADR-014
 * recursion applies on the low-level path (ADR-015).
 */
public sealed interface PatchChange
    permits PatchChange.AttributeChange,
        PatchChange.RelationshipChange,
        PatchChange.ResourceMetaChange,
        PatchChange.RelationshipMetaChange {

  /** Final JSON:API member name (including attribute/relationship renames). */
  String jsonapiName();

  /**
   * Jackson internal property identity on the annotated DTO (Java field, record component, or
   * JavaBean name). Distinct from {@link #jsonapiName()}, which is the configured-Jackson external
   * JSON:API member name.
   */
  String logicalName();

  /** Converted property value; {@code null} means explicit null / null linkage, not omission. */
  @Nullable Object value();

  private static void requireNames(String jsonapiName, String logicalName) {
    Objects.requireNonNull(jsonapiName, "jsonapiName");
    Objects.requireNonNull(logicalName, "logicalName");
  }

  /** A supplied mapped attribute change. */
  record AttributeChange(String jsonapiName, String logicalName, @Nullable Object value)
      implements PatchChange {
    public AttributeChange {
      requireNames(jsonapiName, logicalName);
      value = PatchValues.freeze(value);
    }

    @Override
    public @Nullable Object value() {
      return PatchValues.expose(value);
    }
  }

  /**
   * A supplied mapped relationship linkage replacement.
   *
   * <p>When the converted value is a {@code ResourceIdentifier}, a {@link
   * com.kazforge.jsonapi.jackson.mapping.RelationshipLinkage}, or a collection of either,
   * identifier meta rides on those values. There is no separate identifier-meta change; supplying
   * linkage replaces the whole linkage including per-identifier meta (ADR-017).
   */
  record RelationshipChange(String jsonapiName, String logicalName, @Nullable Object value)
      implements PatchChange {
    public RelationshipChange {
      requireNames(jsonapiName, logicalName);
      value = PatchValues.freeze(value);
    }

    @Override
    public @Nullable Object value() {
      return PatchValues.expose(value);
    }
  }

  /**
   * A supplied resource-side {@code meta} change.
   *
   * <p>{@link #jsonapiName()} is the fixed {@code "meta"} JSON:API location marker. It is a
   * location marker, not a discriminator: an attribute named {@code meta} is wire-legal, so
   * consumers must dispatch on the sealed variant rather than infer semantics from the name alone.
   * {@link #logicalName()} is the annotated meta property's logical Java name and {@link #value()}
   * the converted atomic meta value or a {@link StructuredPatch} where ADR-014 recursion applies on
   * the low-level path (ADR-015).
   */
  record ResourceMetaChange(String jsonapiName, String logicalName, @Nullable Object value)
      implements PatchChange {
    public ResourceMetaChange {
      requireNames(jsonapiName, logicalName);
      value = PatchValues.freeze(value);
    }

    @Override
    public @Nullable Object value() {
      return PatchValues.expose(value);
    }
  }

  /**
   * A supplied relationship {@code meta} change for a specific mapped relationship.
   *
   * <p>{@link #jsonapiName()} is the referenced relationship's JSON:API member name (pairing it
   * with the sibling {@link RelationshipChange} of the same name); {@link #logicalName()} is the
   * annotated meta property's logical Java name; {@link #value()} is the converted atomic meta
   * value or a {@link StructuredPatch} where ADR-014 recursion applies on the low-level path
   * (ADR-015).
   */
  record RelationshipMetaChange(String jsonapiName, String logicalName, @Nullable Object value)
      implements PatchChange {
    public RelationshipMetaChange {
      requireNames(jsonapiName, logicalName);
      value = PatchValues.freeze(value);
    }

    @Override
    public @Nullable Object value() {
      return PatchValues.expose(value);
    }
  }
}
