package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.patch.PatchPresence;

/**
 * Invalid mixed nested shape: one member is not presence-aware, so the shape is neither an ordinary
 * bean nor a valid presence-aware PATCH shape; declaration validation occurs when the shape is
 * used.
 */
public record MixedAddressPatch(PatchPresence<String> street, String city) {}
