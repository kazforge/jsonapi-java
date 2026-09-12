package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.jackson.patch.PatchPresence;
import java.util.List;

/**
 * Presence-aware PATCH shape with a container inner member, proving that {@code List}/{@code Set}/
 * array/{@code Map} inner types stay atomic replacement values rather than recursing (ADR-014).
 */
public record AddressWithTagsPatch(
    PatchPresence<String> street, PatchPresence<List<String>> tags) {}
