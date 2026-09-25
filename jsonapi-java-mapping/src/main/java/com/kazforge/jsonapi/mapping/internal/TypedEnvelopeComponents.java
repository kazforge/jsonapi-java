package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.ErrorObject;
import com.kazforge.jsonapi.core.model.JsonApiObject;
import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.mapping.DomainData;
import com.kazforge.jsonapi.mapping.IncludedResources;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Bound typed-envelope document members with the same absence/null rules as {@link
 * com.kazforge.jsonapi.core.model.JsonApiDocument}. Collections are defensively copied at
 * construction and exposed as unmodifiable views.
 *
 * <p>This type is unsupported implementation detail for backend cooperation, not consumer SPI, and
 * must not appear in supported backend public signatures.
 */
@NullMarked
public record TypedEnvelopeComponents(
    @Nullable DomainData data,
    @Nullable List<ErrorObject> errors,
    @Nullable Meta meta,
    @Nullable JsonApiObject jsonapi,
    @Nullable Links links,
    @Nullable IncludedResources included,
    Map<String, @Nullable Object> additionalMembers) {

  public TypedEnvelopeComponents {
    errors = errors == null ? null : List.copyOf(errors);
    additionalMembers = copyAdditionalMembers(additionalMembers);
  }

  private static Map<String, @Nullable Object> copyAdditionalMembers(
      Map<String, @Nullable Object> members) {
    return Collections.unmodifiableMap(
        new LinkedHashMap<String, @Nullable Object>(Objects.requireNonNull(members, "members")));
  }
}
