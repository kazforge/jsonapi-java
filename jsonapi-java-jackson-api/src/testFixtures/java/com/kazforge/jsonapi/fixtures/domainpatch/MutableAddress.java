package com.kazforge.jsonapi.fixtures.domainpatch;

import java.util.Objects;

/**
 * Ordinary non-record JavaBean-style structured domain value type proving the low-level
 * traversable-bean + object-wire boundary applies to ordinary Jackson-bean semantics, not record
 * components specifically (ADR-014).
 */
public final class MutableAddress {

  private String street;
  private String city;

  public MutableAddress() {}

  public MutableAddress(String street, String city) {
    this.street = street;
    this.city = city;
  }

  public String getStreet() {
    return street;
  }

  public void setStreet(String street) {
    this.street = street;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof MutableAddress that)) {
      return false;
    }
    return Objects.equals(street, that.street) && Objects.equals(city, that.city);
  }

  @Override
  public int hashCode() {
    return Objects.hash(street, city);
  }
}
