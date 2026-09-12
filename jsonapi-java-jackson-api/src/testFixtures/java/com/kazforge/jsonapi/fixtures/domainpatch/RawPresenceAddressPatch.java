package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.jackson.patch.PatchPresence;

/** Invalid nested shape: a raw {@code PatchPresence} member (no type argument). */
@SuppressWarnings({"rawtypes"})
public record RawPresenceAddressPatch(PatchPresence street, PatchPresence<String> city) {}
