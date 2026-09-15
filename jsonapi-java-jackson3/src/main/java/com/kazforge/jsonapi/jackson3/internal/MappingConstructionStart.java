package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.jackson.diagnostic.MappingLocation;
import tools.jackson.databind.JavaType;

/**
 * Mapping-owned construction-path translation start for one top-level synthetic-map key: the
 * member's resource-relative location prefix plus its declared type for nested walking.
 *
 * <p>Owned by mapping metadata so the write and read mappings do not depend on binder
 * implementations for their construction-translation shape. Both the flat-read translator and the
 * recursive PATCH binder consume this value.
 */
record MappingConstructionStart(MappingLocation location, JavaType declaredType) {}
