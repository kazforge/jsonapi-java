package com.kazforge.jsonapi.jackson2.contract

import com.fasterxml.jackson.databind.json.JsonMapper
import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.fixtures.contract.LevelOneFacetCharacterizationSpec
import com.kazforge.jsonapi.jackson2.JsonApiJackson2
import com.kazforge.jsonapi.representation.IncludePolicy
import com.kazforge.jsonapi.representation.RepresentationPolicy

/**
 * Jackson 2 binding of the shared Level-1 facet characterization contract. The runtime policy
 * allows include traversal so authored compound documents can be observed.
 */
class Jackson2LevelOneFacetCharacterizationSpec extends LevelOneFacetCharacterizationSpec {

  @Override
  protected JsonApi api(String jsonApiVersion) {
    def builder = JsonApiJackson2.builder(JsonMapper.builder().build())
        .representationPolicy(
        RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()))
    if (jsonApiVersion != null) {
      builder.jsonApiVersion(jsonApiVersion)
    }
    builder.build()
  }
}
