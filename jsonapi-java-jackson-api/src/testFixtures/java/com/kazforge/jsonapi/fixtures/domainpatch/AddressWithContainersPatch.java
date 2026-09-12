package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.jackson.patch.PatchPresence;
import java.util.Map;
import java.util.Set;

/**
 * Presence-aware PATCH shape with {@code Set}/{@code array}/{@code Map} inner members, proving the
 * typed path treats each as one atomic replacement value rather than recursing into elements or map
 * keys (ADR-014).
 */
public record AddressWithContainersPatch(
    PatchPresence<String> street,
    PatchPresence<Set<String>> aliases,
    PatchPresence<String[]> initials,
    PatchPresence<Map<String, Integer>> scores) {}
