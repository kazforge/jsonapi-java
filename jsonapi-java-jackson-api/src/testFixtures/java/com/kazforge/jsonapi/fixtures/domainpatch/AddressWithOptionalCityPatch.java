package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.jackson.patch.PatchPresence;
import java.util.Optional;

/** Presence-aware PATCH shape with a nested {@code PatchPresence<Optional<String>>} member. */
public record AddressWithOptionalCityPatch(
    PatchPresence<String> street, PatchPresence<Optional<String>> city) {}
