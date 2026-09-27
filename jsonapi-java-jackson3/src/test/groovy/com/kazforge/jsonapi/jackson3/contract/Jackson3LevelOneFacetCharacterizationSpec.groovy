package com.kazforge.jsonapi.jackson3.contract

import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.fixtures.contract.LevelOneFacetCharacterizationSpec
import com.kazforge.jsonapi.jackson3.JsonApiJackson3
import com.kazforge.jsonapi.representation.IncludePolicy
import com.kazforge.jsonapi.representation.RepresentationPolicy
import tools.jackson.databind.json.JsonMapper

/**
 * Jackson 3 binding of the shared Level-1 facet characterization contract. The runtime policy
 * allows include traversal so authored compound documents can be observed.
 */
class Jackson3LevelOneFacetCharacterizationSpec extends LevelOneFacetCharacterizationSpec {

  @Override
  protected JsonApi api(String jsonApiVersion) {
    def builder = JsonApiJackson3.builder(JsonMapper.builder().build())
        .representationPolicy(
        RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()))
    if (jsonApiVersion != null) {
      builder.jsonApiVersion(jsonApiVersion)
    }
    builder.build()
  }
}
