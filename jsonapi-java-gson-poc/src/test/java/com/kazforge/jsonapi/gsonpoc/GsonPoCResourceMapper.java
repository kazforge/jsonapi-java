package com.kazforge.jsonapi.gsonpoc;

import com.google.gson.Gson;
import com.kazforge.jsonapi.core.model.DocumentData;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.jackson.representation.IncludePolicy;
import com.kazforge.jsonapi.jackson.representation.RepresentationPolicy;
import com.kazforge.jsonapi.jackson.representation.RepresentationSelection;
import com.kazforge.jsonapi.mapping.contract.MappingContractResult;
import com.kazforge.jsonapi.mapping.internal.GenericDomainResourceWriter;
import com.kazforge.jsonapi.mapping.internal.MappingRepresentation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/** Tiny test-only facade proving Gson can use the same backend-neutral mapping-domain writer. */
final class GsonPoCResourceMapper {

  private final GenericDomainResourceWriter<Type, Field> writer;

  GsonPoCResourceMapper(Gson gson) {
    this.writer = new GenericDomainResourceWriter<>(new GsonPrototypeMappingBackend(gson));
  }

  ResourceObject toResource(Object resource) {
    return writer.toResource(resource);
  }

  JsonApiDocument toDocument(Object resource, List<String> includePaths) {
    Type type = writer.inferredType(resource);
    ResourceObject primary = writer.toResource(resource, type);

    RepresentationSelection.Builder selection = RepresentationSelection.builder();
    for (String path : includePaths) {
      selection.include(path);
    }
    MappingRepresentation representation =
        new MappingRepresentation(
            selection.build(),
            RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()));

    List<ResourceObject> included =
        writer.collectIncluded(resource, type, primary, representation).included();

    return new JsonApiDocument(
        new DocumentData.SingleResource(primary), null, null, null, null, included, Map.of());
  }

  MappingContractResult toMappedDocument(
      Object resource, List<String> includePaths, Map<String, List<String>> fieldsets) {
    Type type = writer.inferredType(resource);
    RepresentationSelection.Builder selection = RepresentationSelection.builder();
    for (String path : includePaths) {
      selection.include(path);
    }
    for (Map.Entry<String, List<String>> entry : fieldsets.entrySet()) {
      selection.fields(entry.getKey(), entry.getValue());
    }
    MappingRepresentation representation =
        new MappingRepresentation(
            selection.build(),
            RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()));

    ResourceObject primary = writer.toResource(resource, type, representation);
    var includedResult = writer.collectIncluded(resource, type, primary, representation);
    JsonApiDocument document =
        new JsonApiDocument(
            new DocumentData.SingleResource(primary),
            null,
            null,
            null,
            null,
            includedResult.included(),
            Map.of());
    return new MappingContractResult(document, includedResult.sparseFieldsetLinkageExemptions());
  }
}
