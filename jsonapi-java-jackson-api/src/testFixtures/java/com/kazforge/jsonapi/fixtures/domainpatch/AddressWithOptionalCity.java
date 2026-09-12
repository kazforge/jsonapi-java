package com.kazforge.jsonapi.fixtures.domainpatch;

import java.util.Optional;

/** Ordinary structured domain value type with an {@code Optional} nested member (ADR-014). */
public record AddressWithOptionalCity(String street, Optional<String> city) {}
