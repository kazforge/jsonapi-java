package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.diagnostic.MappingLocation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Immutable result of the shared read phase: the nullable-value-preserving synthetic input map plus
 * the wire locations of the supplied identity roles.
 *
 * <p>The map is keyed by backend external name and preserves the absent-versus-explicit-null
 * distinction, so a present JSON null stays an entry with a null value while an omitted member is
 * absent. It already includes the bound identity, attribute, relationship, and meta members the
 * shared reader produced. {@code identifierLocation} and {@code localIdLocation} are present
 * exactly when the corresponding role was supplied, and feed the adapter's existing
 * construction-path translation. The adapter builds construction-path metadata and performs the
 * single configured bean construction. This is unsupported implementation detail for backend
 * cooperation, not consumer SPI.
 */
@NullMarked
public record BasicReadResult(
    Map<String, @Nullable Object> properties,
    @Nullable MappingLocation identifierLocation,
    @Nullable MappingLocation localIdLocation) {

  public BasicReadResult {
    Objects.requireNonNull(properties, "properties");
    // Map.copyOf rejects null values, so keep a defensive unmodifiable LinkedHashMap instead.
    properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
  }
}
