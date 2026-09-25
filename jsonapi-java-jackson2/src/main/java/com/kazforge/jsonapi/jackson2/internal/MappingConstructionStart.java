package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.kazforge.jsonapi.diagnostic.MappingLocation;

/**
 * Mapping-owned construction-path translation start for one top-level synthetic-map key: the
 * member's resource-relative location prefix plus its declared type for nested walking.
 *
 * <p>Owned by mapping metadata so the write mapping does not depend on binder implementations for
 * its construction-translation shape. Typed-PATCH DTO construction consumes this value through
 * {@code ResourceMapping.constructionStartsByJacksonName} and {@code
 * StructuredValueBinder.translateConstructionPath}. Ordinary flat reads use mapping {@link
 * com.kazforge.jsonapi.mapping.internal.ReadConstructionStart} instead.
 */
record MappingConstructionStart(MappingLocation location, JavaType declaredType) {}
