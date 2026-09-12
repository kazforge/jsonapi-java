package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.jackson.patch.PatchPresence;

/**
 * Invalid nested shape: a member declared directly as {@link PatchPresence.Present} rather than
 * {@code PatchPresence<T>}.
 */
public record DirectPresentAddressPatch(
    PatchPresence.Present<String> street, PatchPresence<String> city) {}
