package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.jackson.patch.PatchPresence;

/** Deeper nested presence-aware PATCH shape proving multi-level typed recursion (ADR-014). */
public record GeoPatch(PatchPresence<String> lat, PatchPresence<String> lon) {}
