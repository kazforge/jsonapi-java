package com.kazforge.jsonapi.fixtures.domainpatch;

/** Ordinary structured domain value type with a nested structured member (ADR-013). */
public record AddressWithGeo(String street, Geo geo) {}
