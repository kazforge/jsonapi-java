package com.kazforge.jsonapi.jackson.mapping;

import com.kazforge.jsonapi.core.model.Links;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Additive resource-level decoration for domain writes.
 *
 * <p>Carries only:
 *
 * <ul>
 *   <li>{@code ResourceObject.links}, and
 *   <li>per-relationship {@code Relationship.links} for existing mapped relationships
 * </ul>
 *
 * It never replaces type, id/lid, attributes, relationship linkage/data, resource meta,
 * relationship meta, identifier meta, inclusion membership, or sparse-fieldset provenance, and it
 * never creates a relationship that normal mapping did not produce. Relationship entries are keyed
 * by the mapped property <em>identity</em> — the Jackson logical name (e.g. {@code "comments"} for
 * a Java field named {@code comments}) — not the final wire name. The mapper applies each entry
 * under the configured-Jackson external name automatically, so
 * {@code @JsonProperty("article-comments")} does not require a second key.
 *
 * <p>Decoration is immutable: the relationship map is defensively copied and unmodifiable. Null
 * keys, null values, and empty property names are rejected. Use {@link #builder()} for incremental
 * assembly or {@link #empty()} when no decoration is needed.
 */
public final class ResourceDecoration {

  private static final ResourceDecoration EMPTY = new ResourceDecoration(null, Map.of());

  private final @Nullable Links links;
  private final Map<String, RelationshipDecoration> relationships;

  private ResourceDecoration(
      @Nullable Links links, Map<String, RelationshipDecoration> relationships) {
    this.links = links;
    Map<String, RelationshipDecoration> copy = new LinkedHashMap<>();
    for (Map.Entry<String, RelationshipDecoration> entry : relationships.entrySet()) {
      String key = Objects.requireNonNull(entry.getKey(), "relationship property");
      if (key.isEmpty()) {
        throw new IllegalArgumentException("relationship property must not be empty");
      }
      RelationshipDecoration value =
          Objects.requireNonNull(entry.getValue(), "relationship decoration for " + key);
      copy.put(key, value);
    }
    this.relationships = Collections.unmodifiableMap(copy);
  }

  /** Returns an empty decoration (no resource links, no relationship decorations). */
  public static ResourceDecoration empty() {
    return EMPTY;
  }

  /** Returns a decoration that contributes only resource links. */
  public static ResourceDecoration ofLinks(Links links) {
    return new ResourceDecoration(Objects.requireNonNull(links, "links"), Map.of());
  }

  /** Creates a builder for one immutable decoration. */
  public static Builder builder() {
    return new Builder();
  }

  /** Resource links to add, or {@code null} when none. */
  public @Nullable Links links() {
    return links;
  }

  /**
   * Relationship decorations by mapped property identity (logical name), unmodifiable. Values are
   * never {@code null}; keys are never {@code null} or empty.
   */
  public Map<String, RelationshipDecoration> relationships() {
    return relationships;
  }

  /**
   * Returns {@code true} when this decoration carries neither resource nor relationship links. A
   * present-empty {@link Links} ({@code Links.empty()} / {@code "links":{}}) is a wire-visible
   * value and is not considered empty; only {@code null} means absence.
   */
  public boolean isEmpty() {
    if (links != null) {
      return false;
    }
    for (RelationshipDecoration decoration : relationships.values()) {
      if (!decoration.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ResourceDecoration that)) {
      return false;
    }
    return Objects.equals(links, that.links) && relationships.equals(that.relationships);
  }

  @Override
  public int hashCode() {
    return Objects.hash(links, relationships);
  }

  @Override
  public String toString() {
    return "ResourceDecoration[links=" + links + ", relationships=" + relationships + "]";
  }

  /** Mutable builder whose {@link #build()} result defensively copies supplied values. */
  public static final class Builder {

    private @Nullable Links links;
    private final Map<String, RelationshipDecoration> relationships = new LinkedHashMap<>();

    private Builder() {}

    /** Sets resource links (replaces any previous value). */
    public Builder links(Links links) {
      this.links = Objects.requireNonNull(links, "links");
      return this;
    }

    /**
     * Adds decoration for one mapped relationship property identity (logical name).
     *
     * @param propertyIdentity the Jackson logical property name (e.g. {@code "comments"})
     * @param decoration the decoration to apply when that relationship survives fieldset selection
     */
    public Builder relationship(String propertyIdentity, RelationshipDecoration decoration) {
      Objects.requireNonNull(propertyIdentity, "propertyIdentity");
      if (propertyIdentity.isEmpty()) {
        throw new IllegalArgumentException("propertyIdentity must not be empty");
      }
      Objects.requireNonNull(decoration, "decoration");
      if (relationships.containsKey(propertyIdentity)) {
        throw new IllegalArgumentException(
            "Duplicate relationship decoration for '" + propertyIdentity + "'");
      }
      relationships.put(propertyIdentity, decoration);
      return this;
    }

    /** Convenience that builds a {@link RelationshipDecoration} from the given links. */
    public Builder relationship(String propertyIdentity, Links links) {
      return relationship(propertyIdentity, RelationshipDecoration.of(links));
    }

    /** Builds an immutable decoration. */
    public ResourceDecoration build() {
      return new ResourceDecoration(links, relationships);
    }
  }
}
