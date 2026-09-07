package io.github.kazemek.jsonapi.jackson2;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.github.kazemek.jsonapi.annotation.JsonApiId;
import io.github.kazemek.jsonapi.annotation.JsonApiMeta;
import io.github.kazemek.jsonapi.annotation.JsonApiResource;
import java.util.Objects;

/**
 * Jackson 2 polymorphic whole-meta targets owned by {@code PolymorphicMetaSpec}: root {@code
 * TypeDeserializer} decoration for concrete and abstract polymorphic meta types. Adapter-local
 * Jackson 2 copy of the shared shapes.
 */
@SuppressWarnings({"unused", "NullAway"})
public final class PolymorphicMetaFixtures {

  private PolymorphicMetaFixtures() {}

  /**
   * Concrete POJO carrying root {@code @JsonTypeInfo}; its root deserializer is wrapped by a {@code
   * TypeDeserializer} (decorated), yet it is still an object-shaped whole-meta target (ADR-015).
   */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
  @JsonSubTypes({@JsonSubTypes.Type(value = ConcreteTypedMeta.class, name = "concrete")})
  public static class ConcreteTypedMeta {

    private String value;

    public ConcreteTypedMeta() {}

    public ConcreteTypedMeta(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof ConcreteTypedMeta that)) {
        return false;
      }
      return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }
  }

  /** Domain model with a concrete root-polymorphic whole-meta target. */
  @JsonApiResource(type = "articles")
  public record ConcreteTypedMetaArticle(
      @JsonApiId String id, @JsonApiMeta ConcreteTypedMeta meta) {}

  /**
   * Abstract polymorphic whole-meta base type: Jackson materializes the concrete subtype from the
   * {@code kind} discriminator through the property/root {@code TypeDeserializer} (ADR-015).
   */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
  @JsonSubTypes({@JsonSubTypes.Type(value = SourceMeta.class, name = "source")})
  public abstract static class PolyMetaBase {

    private String note;

    public PolyMetaBase() {}

    public PolyMetaBase(String note) {
      this.note = note;
    }

    public String getNote() {
      return note;
    }

    public void setNote(String note) {
      this.note = note;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof PolyMetaBase that)) {
        return false;
      }
      return Objects.equals(note, that.note);
    }

    @Override
    public int hashCode() {
      return Objects.hash(note);
    }
  }

  /** Concrete {@link PolyMetaBase} subtype selected from the {@code kind} discriminator. */
  public static final class SourceMeta extends PolyMetaBase {

    private String source;

    public SourceMeta() {}

    public SourceMeta(String source, String note) {
      super(note);
      this.source = source;
    }

    public String getSource() {
      return source;
    }

    public void setSource(String source) {
      this.source = source;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof SourceMeta that)) {
        return false;
      }
      return Objects.equals(source, that.source) && super.equals(that);
    }

    @Override
    public int hashCode() {
      return Objects.hash(source, super.hashCode());
    }
  }

  /** Domain model with an abstract polymorphic whole-meta target. */
  @JsonApiResource(type = "articles")
  public record PolyMetaArticle(@JsonApiId String id, @JsonApiMeta PolyMetaBase meta) {}
}
