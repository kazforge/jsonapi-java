package com.kazforge.jsonapi.fixtures.contract;

import com.kazforge.jsonapi.core.model.ErrorObject;
import com.kazforge.jsonapi.core.model.JsonApiObject;
import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.mapping.DomainData;
import com.kazforge.jsonapi.mapping.IncludedResources;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Neutral observation of a bound typed envelope. Adapter subclasses pass the envelope members and
 * {@code metaAs(Class)} projection through without copying so unmodifiability stays observable.
 */
public record BoundTypedEnvelope(
    @Nullable DomainData data,
    @Nullable List<ErrorObject> errors,
    @Nullable Meta meta,
    @Nullable JsonApiObject jsonapi,
    @Nullable Links links,
    @Nullable IncludedResources included,
    Map<String, @Nullable Object> additionalMembers,
    MetaProjection metaProjection) {

  /**
   * Adapter-supplied {@code metaAs(Class)} projection retained from the native envelope without
   * copying converted values.
   */
  @FunctionalInterface
  public interface MetaProjection {

    <T> @Nullable T as(Class<T> targetType);
  }

  /** Converts document meta through the adapter's native {@code metaAs(Class)} projection. */
  public <T> @Nullable T metaAs(Class<T> targetType) {
    return metaProjection.as(targetType);
  }
}
