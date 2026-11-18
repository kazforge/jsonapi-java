package com.kazforge.jsonapi.mapping;

import com.kazforge.jsonapi.diagnostic.MappingLocation;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Exact request attribution for one logical property path in a successfully bound primary DTO.
 *
 * <p>Only present states carry a location, always document-relative (for example {@code
 * /data/attributes/address/postal_code}). A location addresses an actually supplied value, never an
 * inferred or nearest-ancestor location. Omitted and unresolved states have no location. Presence
 * describes the wire input, not the converted DTO value or an instruction to apply a change. In
 * particular, explicit null can convert to {@code Optional.empty()}, and an omitted member of an
 * atomic replacement object is not necessarily an instruction to preserve old state.
 *
 * @param presence whether the member was omitted, supplied, or cannot be attributed
 * @param location the exact supplied value's location, only for present states
 */
public record BindingSource(Presence presence, @Nullable MappingLocation location) {

  public BindingSource {
    Objects.requireNonNull(presence, "presence");
    boolean present = presence == Presence.PRESENT_NULL || presence == Presence.PRESENT_VALUE;
    if (present == (location == null)) {
      throw new IllegalArgumentException("Only present binding sources require a location");
    }
  }

  /** Wire presence, independently of defaults, null handling, or other configured conversion. */
  public enum Presence {
    /** A known bindable member is absent from its supplied containing object. */
    OMITTED,
    /** The request supplied an explicit JSON null, including null relationship linkage. */
    PRESENT_NULL,
    /** The request supplied a non-null value, including an empty object or array. */
    PRESENT_VALUE,
    /** The mapping or correspondence cannot establish this path exactly. */
    UNRESOLVED
  }
}
