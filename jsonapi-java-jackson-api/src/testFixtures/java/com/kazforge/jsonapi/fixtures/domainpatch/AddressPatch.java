package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.jackson.patch.PatchPresence;

/**
 * Nested presence-aware PATCH shape for a structured address value (typed-path recursion).
 *
 * <p>Every visible member is exactly {@code PatchPresence<T>}, so this type qualifies as a
 * presence-aware nested PATCH shape (ADR-014). It is a plain Jackson bean, not a
 * {@code @JsonApiResource}.
 */
public record AddressPatch(PatchPresence<String> street, PatchPresence<String> city) {}
