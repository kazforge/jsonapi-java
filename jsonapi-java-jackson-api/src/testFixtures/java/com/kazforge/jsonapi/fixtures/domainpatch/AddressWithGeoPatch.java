package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.jackson.patch.PatchPresence;

/** Presence-aware PATCH shape with a deeper nested {@link GeoPatch} member (ADR-014). */
public record AddressWithGeoPatch(PatchPresence<String> street, PatchPresence<GeoPatch> geo) {}
