package com.kazforge.jsonapi.mapping.contract;

import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.core.model.ResourceObject;
import java.util.List;
import java.util.Map;

/** Black-box adapter used only by the shared mapping contract tests. */
public interface MappingContractAdapter {

  ResourceObject toResource(Object resource);

  JsonApiDocument toDocument(Object resource, List<String> includePaths);

  MappingContractResult toMappedDocument(
      Object resource,
      List<String> includePaths,
      Map<String, List<String>> fieldsets);
}
