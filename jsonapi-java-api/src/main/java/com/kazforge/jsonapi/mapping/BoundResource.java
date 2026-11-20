package com.kazforge.jsonapi.mapping;

import java.util.List;

/**
 * A primary resource DTO together with immutable, request-local source attribution.
 *
 * <p>Source lookup neither reads the DTO nor performs binding or validation. Later DTO mutation
 * does not change attribution. Metadata exposes no raw input or property values.
 *
 * @param <T> the bound DTO type
 */
public interface BoundResource<T> {

  /** Returns the DTO constructed by this binding operation, without copying or rebinding it. */
  T resource();

  /**
   * Looks up raw logical Java property-name segments, with canonical non-negative decimal indices
   * where ordered input/output correspondence is established. Empty path denotes the primary
   * resource at {@code /data}. Names are not dotted paths, JSON Pointers, or a validation-path
   * grammar; numeric names remain property names when traversing an object.
   *
   * <p>{@code PatchPresence} and {@code Optional} wrappers are transparent and contribute no path
   * segment. Callers adapting a validation path remove wrapper-extraction nodes using the
   * validator's node/type information, never by blindly removing strings named {@code value}: real
   * properties with that name remain significant.
   *
   * <p>Unknown or ambiguous mappings, descendants of null/omitted parents, opaque conversions,
   * unsupported container correspondence, invalid indices, and unrepresentable pointer segments
   * return {@link BindingSource.Presence#UNRESOLVED}. Known omission applies only to a mapped
   * bindable member in its supplied containing object. The lookup never returns a guessed ancestor
   * pointer; a caller may explicitly query a shorter meaningful path instead. Relationships address
   * linkage, never hydrated included resources.
   *
   * @param logicalPath the raw segments; neither the list nor its elements may be null
   * @return wire presence and an exact document-relative location only for a supplied value
   * @throws NullPointerException when the path or any segment is null
   */
  BindingSource source(List<String> logicalPath);
}
