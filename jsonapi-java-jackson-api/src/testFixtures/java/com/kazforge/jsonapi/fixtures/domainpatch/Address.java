package com.kazforge.jsonapi.fixtures.domainpatch;

/**
 * Ordinary structured domain value type used by the low-level {@code PatchCommand} path: recursion
 * derives supplied-only nested changes from this shape under the traversable-bean + object-wire
 * boundary (ADR-014).
 */
public record Address(String street, String city) {}
