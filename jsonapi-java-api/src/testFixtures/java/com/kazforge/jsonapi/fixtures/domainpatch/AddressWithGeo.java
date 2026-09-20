package com.kazforge.jsonapi.fixtures.domainpatch;

/** Ordinary structured domain value type with a nested structured member. */
public record AddressWithGeo(String street, Geo geo) {}
