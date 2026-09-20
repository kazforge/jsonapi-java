package com.kazforge.jsonapi.jackson2.contract

import com.fasterxml.jackson.databind.json.JsonMapper
import com.kazforge.jsonapi.fixtures.contract.InclusionFieldsetCharacterizationSpec
import com.kazforge.jsonapi.jackson.api.JsonApi
import com.kazforge.jsonapi.jackson.representation.IncludePolicy
import com.kazforge.jsonapi.jackson.representation.RepresentationPolicy
import com.kazforge.jsonapi.jackson2.JsonApiJackson2

/**
 * Jackson 2 binding of the shared inclusion and fieldset characterization contract. The runtime
 * policy allows include traversal, since the application-lifetime include policy is runtime
 * configuration and the default runtime policy denies it.
 */
class Jackson2InclusionFieldsetCharacterizationSpec extends InclusionFieldsetCharacterizationSpec {

  @Override
  protected JsonApi api() {
    JsonApiJackson2.builder(JsonMapper.builder().build())
        .representationPolicy(RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()))
        .build()
  }
}
