package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.diagnostic.MappingLocation;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/**
 * Neutral construction-path translation start for one ordinary flat-read synthetic-map key: the
 * member's resource-relative JSON:API location paired with the same opaque backend property token
 * the read definition already carries.
 *
 * <p>The location is assembled once by {@link
 * ReadResourceDefinition#constructionStarts(MappingLocation, MappingLocation)} so both Jackson
 * adapters cannot drift in their backend-name to JSON:API-location translation. The paired {@link
 * ReadProperty} keeps the effective native property available to the adapter's nested path walker
 * without a second lookup and without a separately supplied effective-type token; the native type
 * stays owned by the adapter through that token. This is unsupported implementation detail for
 * backend cooperation, not consumer SPI.
 *
 * @param <P> opaque backend-native property token
 */
@NullMarked
public record ReadConstructionStart<P>(MappingLocation location, ReadProperty<P> property) {

  public ReadConstructionStart {
    Objects.requireNonNull(location, "location");
    Objects.requireNonNull(property, "property");
  }
}
