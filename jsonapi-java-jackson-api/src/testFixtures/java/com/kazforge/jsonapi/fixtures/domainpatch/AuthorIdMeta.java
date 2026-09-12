package com.kazforge.jsonapi.fixtures.domainpatch;

import org.jspecify.annotations.Nullable;

/**
 * Shared application-owned identifier-side meta for to-one author linkage. Distinct from {@link
 * AuthorMeta} (relationship-level {@code displayName}).
 */
public record AuthorIdMeta(@Nullable String role) {}
