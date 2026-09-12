package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.jackson.patch.PatchPresence;

/**
 * Invalid mixed nested shape: one member is not presence-aware, so the shape is neither an ordinary
 * bean nor a valid presence-aware PATCH shape (ADR-014 lazy declaration validation).
 */
public record MixedAddressPatch(PatchPresence<String> street, String city) {}
