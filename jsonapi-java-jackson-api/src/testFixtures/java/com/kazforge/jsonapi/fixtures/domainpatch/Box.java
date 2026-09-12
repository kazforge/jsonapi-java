package com.kazforge.jsonapi.fixtures.domainpatch;

import java.util.List;

/**
 * Ordinary generic structured domain value type. The {@code T} type variable must be substituted
 * from the enclosing {@code JavaType} (e.g. {@code Box<Integer>}) during recursive shape resolution
 * so the member resolves as {@code List<Integer>}; a raw {@code Box} would retain {@code T} and
 * fail {@code Integer} element conversion (ADR-014).
 */
public record Box<T>(List<T> numbers) {}
