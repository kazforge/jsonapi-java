package com.kazforge.jsonapi.jackson3;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * Presence-aware nested PATCH shapes owned by {@code PatchStructuredBindingSpec}: naming-strategy
 * traversal, wrapper-level customization rejection (deserialization and serialization sides), and
 * deep construction-failure pointer translation (ADR-014). Each nested shape exists to isolate one
 * declaration-validation or diagnostic-translation mechanic.
 */
@SuppressWarnings({"unused", "NullAway"})
public final class PatchStructureFixtures {

  private PatchStructureFixtures() {}

  /**
   * Ordinary non-record structured domain value type with a multi-word member, proving the naming
   * strategy applies to low-level structured traversal and that {@code wireName} / {@code
   * logicalName} divergence is preserved in the {@link
   * com.kazforge.jsonapi.jackson.patch.StructuredPatch} (ADR-014).
   */
  public static final class SnakeAddress {

    private String streetName;

    public SnakeAddress() {}

    public SnakeAddress(String streetName) {
      this.streetName = streetName;
    }

    public String getStreetName() {
      return streetName;
    }

    public void setStreetName(String streetName) {
      this.streetName = streetName;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof SnakeAddress that)) {
        return false;
      }
      return Objects.equals(streetName, that.streetName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(streetName);
    }
  }

  /**
   * JavaBean-style presence-aware nested PATCH shape with a multi-word member, used to prove the
   * naming strategy applies to nested structured marker maps (ADR-014).
   */
  public static final class SnakeAddressPatch {

    private PatchPresence<String> streetName;
    private PatchPresence<String> city;

    public SnakeAddressPatch() {}

    public SnakeAddressPatch(PatchPresence<String> streetName, PatchPresence<String> city) {
      this.streetName = streetName;
      this.city = city;
    }

    public PatchPresence<String> getStreetName() {
      return streetName;
    }

    public void setStreetName(PatchPresence<String> streetName) {
      this.streetName = streetName;
    }

    public PatchPresence<String> getCity() {
      return city;
    }

    public void setCity(PatchPresence<String> city) {
      this.city = city;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof SnakeAddressPatch that)) {
        return false;
      }
      return Objects.equals(streetName, that.streetName) && Objects.equals(city, that.city);
    }

    @Override
    public int hashCode() {
      return Objects.hash(streetName, city);
    }
  }

  /** Low-level PATCH DTO wrapping {@link StructuredRecursionFixtures.AddressWithLoudNote}. */
  @JsonApiResource(type = "articles")
  public record AddressWithLoudNoteArticle(
      @JsonApiId String id,
      @JsonApiAttribute StructuredRecursionFixtures.AddressWithLoudNote address) {}

  /**
   * Presence-aware nested PATCH shape with a getter-level {@code @JsonDeserialize}-customized
   * member, proving nested wrapper customization is rejected on shape entry (ADR-014).
   */
  public static final class WrapperCustomizedAddressPatch {

    private PatchPresence<String> street;
    private PatchPresence<String> city;

    public WrapperCustomizedAddressPatch() {}

    public WrapperCustomizedAddressPatch(PatchPresence<String> street, PatchPresence<String> city) {
      this.street = street;
      this.city = city;
    }

    public PatchPresence<String> getStreet() {
      return street;
    }

    public void setStreet(PatchPresence<String> street) {
      this.street = street;
    }

    @JsonDeserialize(using = StructuredRecursionFixtures.UpperCaseStringDeserializer.class)
    public PatchPresence<String> getCity() {
      return city;
    }

    public void setCity(PatchPresence<String> city) {
      this.city = city;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof WrapperCustomizedAddressPatch that)) {
        return false;
      }
      return Objects.equals(street, that.street) && Objects.equals(city, that.city);
    }

    @Override
    public int hashCode() {
      return Objects.hash(street, city);
    }
  }

  /**
   * Presence-aware nested PATCH shape (JavaBean-style) with wrapper-level {@code @JsonDeserialize}
   * on the {@code city} setter, proving deserialization-side customization on a setter is detected
   * and rejected on typed shape entry (ADR-014).
   */
  public static final class SetterCustomizedAddressPatch {

    private PatchPresence<String> street;
    private PatchPresence<String> city;

    public SetterCustomizedAddressPatch() {}

    public SetterCustomizedAddressPatch(PatchPresence<String> street, PatchPresence<String> city) {
      this.street = street;
      this.city = city;
    }

    public PatchPresence<String> getStreet() {
      return street;
    }

    public void setStreet(PatchPresence<String> street) {
      this.street = street;
    }

    public PatchPresence<String> getCity() {
      return city;
    }

    @JsonDeserialize(using = StructuredRecursionFixtures.UpperCaseStringDeserializer.class)
    public void setCity(PatchPresence<String> city) {
      this.city = city;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof SetterCustomizedAddressPatch that)) {
        return false;
      }
      return Objects.equals(street, that.street) && Objects.equals(city, that.city);
    }

    @Override
    public int hashCode() {
      return Objects.hash(street, city);
    }
  }

  /**
   * Marker {@code @JsonSerialize} serializer for a presence-aware member, used to prove a
   * non-getter-side (setter) wrapper-level {@code @JsonSerialize} is detected and rejected on typed
   * shape entry (ADR-014). Never actually invoked: the member is rejected during declaration
   * validation.
   */
  public static final class ConstantPatchPresenceSerializer
      extends ValueSerializer<PatchPresence<String>> {

    @Override
    public void serialize(
        PatchPresence<String> value, JsonGenerator gen, SerializationContext context)
        throws JacksonException {
      gen.writeString("custom");
    }
  }

  /**
   * Presence-aware nested PATCH shape (JavaBean-style) with wrapper-level {@code @JsonSerialize} on
   * the {@code city} setter, proving serialization customization on a non-getter side is detected
   * symmetrically and rejected on typed shape entry (ADR-014).
   */
  public static final class SetterSerializeCustomizedAddressPatch {

    private PatchPresence<String> street;
    private PatchPresence<String> city;

    public SetterSerializeCustomizedAddressPatch() {}

    public SetterSerializeCustomizedAddressPatch(
        PatchPresence<String> street, PatchPresence<String> city) {
      this.street = street;
      this.city = city;
    }

    public PatchPresence<String> getStreet() {
      return street;
    }

    public void setStreet(PatchPresence<String> street) {
      this.street = street;
    }

    public PatchPresence<String> getCity() {
      return city;
    }

    @JsonSerialize(using = ConstantPatchPresenceSerializer.class)
    public void setCity(PatchPresence<String> city) {
      this.city = city;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof SetterSerializeCustomizedAddressPatch that)) {
        return false;
      }
      return Objects.equals(street, that.street) && Objects.equals(city, that.city);
    }

    @Override
    public int hashCode() {
      return Objects.hash(street, city);
    }
  }

  /**
   * Presence-aware nested PATCH shape (record / creator-bound) with wrapper-level
   * {@code @JsonDeserialize} on the {@code city} creator parameter, proving deserialization-side
   * customization on a creator parameter is detected and rejected on typed shape entry (ADR-014).
   */
  public record CreatorCustomizedAddressPatch(
      PatchPresence<String> street,
      @JsonDeserialize(using = StructuredRecursionFixtures.UpperCaseStringDeserializer.class)
          PatchPresence<String> city) {}

  /**
   * Presence-aware nested PATCH shape whose deeper member is a throwing {@link ThrowingGeoPatch},
   * used to prove deep Jackson construction-failure paths are translated to wire-name pointers
   * (ADR-014).
   */
  public static final class ThrowingAddressPatch {

    private PatchPresence<String> street;
    private PatchPresence<ThrowingGeoPatch> geo;

    public ThrowingAddressPatch() {}

    public ThrowingAddressPatch(PatchPresence<String> street, PatchPresence<ThrowingGeoPatch> geo) {
      this.street = street;
      this.geo = geo;
    }

    public PatchPresence<String> getStreet() {
      return street;
    }

    public void setStreet(PatchPresence<String> street) {
      this.street = street;
    }

    public PatchPresence<ThrowingGeoPatch> getGeo() {
      return geo;
    }

    public void setGeo(PatchPresence<ThrowingGeoPatch> geo) {
      this.geo = geo;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof ThrowingAddressPatch that)) {
        return false;
      }
      return Objects.equals(street, that.street) && Objects.equals(geo, that.geo);
    }

    @Override
    public int hashCode() {
      return Objects.hash(street, geo);
    }
  }

  /**
   * Presence-aware nested PATCH shape whose canonical creator throws when constructed from a
   * supplied value, forcing a Jackson construction failure whose deep path must be translated to a
   * wire-name pointer (ADR-014).
   */
  public record ThrowingGeoPatch(PatchPresence<String> lat) {

    public ThrowingGeoPatch {
      if (lat instanceof PatchPresence.Present) {
        throw new IllegalStateException("boom");
      }
    }
  }

  /**
   * Typed PATCH DTO whose canonical constructor always throws, forcing a Jackson construction
   * failure with no property path so the shape-translated pointer falls back to the root (ADR-014).
   */
  @JsonApiResource(type = "articles")
  public record ThrowingArticlePatch(
      @JsonApiId String id, @JsonApiAttribute PatchPresence<String> title) {

    public ThrowingArticlePatch {
      throw new IllegalStateException("boom");
    }
  }
}
