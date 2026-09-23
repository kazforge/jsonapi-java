package com.kazforge.jsonapi.jackson3.contract

import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.fixtures.contract.PatchCommandCharacterizationSpec
import com.kazforge.jsonapi.fixtures.domainread.MappedReadTarget
import com.kazforge.jsonapi.jackson3.JsonApiJackson3
import com.kazforge.jsonapi.jackson3.mapping.RelationshipLinkageMapper
import tools.jackson.databind.JavaType
import tools.jackson.databind.json.JsonMapper

/** Jackson 3 binding of the shared low-level PATCH command characterization contract. */
class Jackson3PatchCommandCharacterizationSpec extends PatchCommandCharacterizationSpec {

  @Override
  protected JsonApi api() {
    JsonApiJackson3.jsonApi(JsonMapper.builder().build())
  }

  @Override
  protected JsonApi mappedApi(boolean nullMapper) {
    JsonApiJackson3.builder(JsonMapper.builder().build())
        .linkageMappers([(MappedReadTarget): mapper(nullMapper)])
        .build()
  }

  private static RelationshipLinkageMapper mapper(boolean nullMapper) {
    { RelationshipData data, JavaType target ->
      if (nullMapper) {
        return null
      }
      if (data instanceof RelationshipData.SingleLinkage) {
        def identifier = ((RelationshipData.SingleLinkage) data).identifier()
        return new MappedReadTarget(identifier.type(), identifier.id())
      }
      ((RelationshipData.IdentifierCollectionLinkage) data).identifiers().collect {
        new MappedReadTarget(it.type(), it.id())
      }
    } as RelationshipLinkageMapper
  }
}
