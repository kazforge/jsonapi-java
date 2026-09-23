package com.kazforge.jsonapi.jackson2;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.patch.PatchPresence;

/**
 * Jackson 2 adapter-local presence-aware nested PATCH shapes for structured-binding regressions:
 * deep construction-failure pointer translation and wrapper-level serialization customization
 * rejection on a presence-aware member.
 */
@SuppressWarnings({"unused", "NullAway"})
public final class PatchStructureFixtures {

  private PatchStructureFixtures() {}

  /** Typed PATCH DTO reaching a nested shape whose deeper member's creator throws. */
  @JsonApiResource(type = "articles")
  public record ThrowingGeoPatchDto(
      @JsonApiId String id, @JsonApiAttribute PatchPresence<ThrowingAddressPatch> address) {}

  /** Presence-aware nested shape whose deeper {@code geo} member throws when supplied. */
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
  }

  /** Deeper presence-aware shape whose canonical creator throws on a supplied value. */
  public record ThrowingGeoPatch(PatchPresence<String> lat) {

    public ThrowingGeoPatch {
      if (lat instanceof PatchPresence.Present) {
        throw new IllegalStateException("boom");
      }
    }
  }

  /** Typed PATCH DTO whose nested shape carries a getter-level {@code @JsonSerialize}. */
  @JsonApiResource(type = "articles")
  public record SerializeCustomizedAddressPatchDto(
      @JsonApiId String id,
      @JsonApiAttribute PatchPresence<SerializeCustomizedAddressPatch> address) {}

  /** Presence-aware nested shape with wrapper-level serialization customization on {@code city}. */
  public static final class SerializeCustomizedAddressPatch {

    private PatchPresence<String> street;
    private PatchPresence<String> city;

    public SerializeCustomizedAddressPatch() {}

    public SerializeCustomizedAddressPatch(
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

    @JsonSerialize(using = ToStringSerializer.class)
    public PatchPresence<String> getCity() {
      return city;
    }

    public void setCity(PatchPresence<String> city) {
      this.city = city;
    }
  }
}
