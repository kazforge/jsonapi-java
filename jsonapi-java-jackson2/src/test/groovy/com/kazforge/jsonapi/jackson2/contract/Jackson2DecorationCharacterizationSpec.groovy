package com.kazforge.jsonapi.jackson2.contract

import com.fasterxml.jackson.databind.json.JsonMapper
import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.fixtures.contract.DecorationCharacterizationSpec
import com.kazforge.jsonapi.jackson2.JsonApiJackson2
import com.kazforge.jsonapi.mapping.ResourceDecoratorRegistry
import com.kazforge.jsonapi.representation.IncludePolicy
import com.kazforge.jsonapi.representation.RepresentationPolicy

/**
 * Jackson 2 binding of the shared additive link-decoration characterization contract. The runtime
 * policy allows include traversal so the included-resource case can be observed; the application
 * include policy is runtime configuration and the default runtime policy denies it.
 */
class Jackson2DecorationCharacterizationSpec extends DecorationCharacterizationSpec {

  @Override
  protected JsonApi api(ResourceDecoratorRegistry decorators) {
    JsonApiJackson2.builder(JsonMapper.builder().build())
        .decorators(decorators)
        .representationPolicy(RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()))
        .build()
  }
}
