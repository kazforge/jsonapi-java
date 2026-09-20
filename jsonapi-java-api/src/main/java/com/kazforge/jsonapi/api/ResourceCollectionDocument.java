package com.kazforge.jsonapi.api;

import com.kazforge.jsonapi.core.model.JsonApiObject;
import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.ResourceObject;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Level-1 typed read result for a resource collection plus selected document-level state.
 *
 * <p>{@link #resources()} is always the present primary {@code data} array in wire order; an empty
 * list preserves {@code "data": []} and never means absent data. For nullable document-level
 * components, Java {@code null} means the member was absent; in particular, a non-null empty {@code
 * included} list preserves {@code "included": []}.
 *
 * <p>Included resources are carried as validated core {@link ResourceObject} values, never bound to
 * DTOs: included state may be heterogeneous while this path stays homogeneous and registry-free.
 * Included resources are never hydrated into relationship properties. The result is returned only
 * after complete document validation. Error documents, identifier primary data, and additional
 * document members are not carried; reads requiring those states use the documents facet or an
 * advanced envelope instead.
 */
public record ResourceCollectionDocument<T>(
    List<T> resources,
    @Nullable Meta meta,
    @Nullable Links links,
    @Nullable JsonApiObject jsonapi,
    @Nullable List<ResourceObject> included) {

  public ResourceCollectionDocument {
    Objects.requireNonNull(resources, "resources");
    for (T resource : resources) {
      Objects.requireNonNull(resource, "resources element");
    }
    resources = List.copyOf(resources);
    if (included != null) {
      for (ResourceObject includedResource : included) {
        Objects.requireNonNull(includedResource, "included element");
      }
      included = List.copyOf(included);
    }
  }
}
