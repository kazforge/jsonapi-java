package com.kazforge.jsonapi.fixtures.domainpatch;

import java.util.Optional;

/** Ordinary structured domain value type with an {@code Optional} nested member. */
public record AddressWithOptionalCity(String street, Optional<String> city) {}
