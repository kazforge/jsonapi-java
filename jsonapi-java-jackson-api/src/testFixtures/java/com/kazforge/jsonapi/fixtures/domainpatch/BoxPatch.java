package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.jackson.patch.PatchPresence;
import java.util.List;

/**
 * Generic presence-aware PATCH shape. The {@code T} type variable must be substituted from the
 * enclosing {@code JavaType} (e.g. {@code BoxPatch<Integer>}) during recursive shape resolution so
 * the member resolves as {@code PatchPresence<List<Integer>>}; a raw {@code BoxPatch} would retain
 * {@code T} and fail {@code Integer} element conversion (ADR-014).
 */
public record BoxPatch<T>(PatchPresence<List<T>> numbers) {}
