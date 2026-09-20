package com.kazforge.jsonapi.jackson3.contract

import com.kazforge.jsonapi.fixtures.contract.InclusionFieldsetCharacterizationSpec
import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.representation.IncludePolicy
import com.kazforge.jsonapi.representation.RepresentationPolicy
import com.kazforge.jsonapi.jackson3.JsonApiJackson3
import tools.jackson.databind.json.JsonMapper

/**
 * Jackson 3 binding of the shared inclusion and fieldset characterization contract. The runtime
 * policy allows include traversal, since the application-lifetime include policy is runtime
 * configuration and the default runtime policy denies it.
 */
class Jackson3InclusionFieldsetCharacterizationSpec extends InclusionFieldsetCharacterizationSpec {

  @Override
  protected JsonApi api() {
    JsonApiJackson3.builder(JsonMapper.builder().build())
        .representationPolicy(RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()))
        .build()
  }
}
