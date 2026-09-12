package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.jackson.patch.PatchPresence;
import java.util.Objects;

/**
 * Ordinary JavaBean-style presence-aware PATCH shape (private fields, default constructor,
 * getter/setter property binding) proving typed-path recursion is ordinary Jackson-bean semantics,
 * not record-specific (ADR-014).
 */
public final class MutableAddressPatch {

  private PatchPresence<String> street;
  private PatchPresence<String> city;

  public MutableAddressPatch() {}

  public MutableAddressPatch(PatchPresence<String> street, PatchPresence<String> city) {
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

  public void setCity(PatchPresence<String> city) {
    this.city = city;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof MutableAddressPatch that)) {
      return false;
    }
    return Objects.equals(street, that.street) && Objects.equals(city, that.city);
  }

  @Override
  public int hashCode() {
    return Objects.hash(street, city);
  }
}
