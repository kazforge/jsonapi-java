package com.kazforge.jsonapi.jackson3.contract

import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.fixtures.contract.DecorationCharacterizationSpec
import com.kazforge.jsonapi.jackson3.JsonApiJackson3
import com.kazforge.jsonapi.mapping.ResourceDecoratorRegistry
import com.kazforge.jsonapi.representation.IncludePolicy
import com.kazforge.jsonapi.representation.RepresentationPolicy
import tools.jackson.databind.json.JsonMapper

/**
 * Jackson 3 binding of the shared additive link-decoration characterization contract. The runtime
 * policy allows include traversal so the included-resource case can be observed; the application
 * include policy is runtime configuration and the default runtime policy denies it.
 */
class Jackson3DecorationCharacterizationSpec extends DecorationCharacterizationSpec {

  @Override
  protected JsonApi api(ResourceDecoratorRegistry decorators) {
    JsonApiJackson3.builder(JsonMapper.builder().build())
        .decorators(decorators)
        .representationPolicy(RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()))
        .build()
  }
}
