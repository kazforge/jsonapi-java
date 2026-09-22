package com.kazforge.jsonapi.jackson2.contract

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.json.JsonMapper
import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.core.model.RelationshipData
import com.kazforge.jsonapi.fixtures.contract.AdvancedReadCharacterizationSpec
import com.kazforge.jsonapi.fixtures.domainread.MappedReadTarget
import com.kazforge.jsonapi.jackson2.JsonApiJackson2
import com.kazforge.jsonapi.jackson2.mapping.RelationshipLinkageMapper

/** Jackson 2 binding of the shared advanced relationship-linkage read characterization contract. */
class Jackson2AdvancedReadCharacterizationSpec extends AdvancedReadCharacterizationSpec {

  @Override
  protected JsonApi api() {
    JsonApiJackson2.jsonApi(JsonMapper.builder().build())
  }

  @Override
  protected JsonApi mappedApi(boolean nullMapper) {
    JsonApiJackson2.builder(JsonMapper.builder().build())
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
