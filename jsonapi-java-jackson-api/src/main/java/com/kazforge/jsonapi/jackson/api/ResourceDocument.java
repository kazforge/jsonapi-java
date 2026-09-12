package com.kazforge.jsonapi.jackson.api;

import com.kazforge.jsonapi.core.model.JsonApiObject;
import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.ResourceObject;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Optional Level-1 typed read result for one resource plus document-level state.
 *
 * <p>Carries the bound primary DTO together with top-level {@code meta}, {@code links}, {@code
 * jsonapi}, and compound {@code included} state. Java {@code null} components mean the member was
 * absent. Included resources are carried as validated core {@link ResourceObject} values in wire
 * order, never bound to DTOs: included state may be heterogeneous while this path stays homogeneous
 * and registry-free, so DTO binding of included resources remains advanced. Included resources are
 * never hydrated into relationship properties. The result is returned only after complete document
 * validation. Error documents, identifier primary data, and additional document members are not
 * carried; reads requiring those states use the documents facet or an advanced envelope instead.
 *
 * @param <T> the bound primary DTO type
 */
public record ResourceDocument<T>(
    T resource,
    @Nullable Meta meta,
    @Nullable Links links,
    @Nullable JsonApiObject jsonapi,
    @Nullable List<ResourceObject> included) {

  public ResourceDocument {
    Objects.requireNonNull(resource, "resource");
    if (included != null) {
      for (ResourceObject includedResource : included) {
        Objects.requireNonNull(includedResource, "included element");
      }
      included = List.copyOf(included);
    }
  }
}
