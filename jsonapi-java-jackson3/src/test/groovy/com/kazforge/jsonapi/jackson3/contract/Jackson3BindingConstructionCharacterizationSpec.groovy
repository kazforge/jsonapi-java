package com.kazforge.jsonapi.jackson3.contract

import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.fixtures.contract.BindingConstructionCharacterizationSpec
import com.kazforge.jsonapi.jackson3.JsonApiJackson3
import tools.jackson.databind.json.JsonMapper

/** Jackson 3 binding of the shared binding-construction characterization contract. */
class Jackson3BindingConstructionCharacterizationSpec extends BindingConstructionCharacterizationSpec {

  @Override
  protected JsonApi api() {
    JsonApiJackson3.jsonApi(JsonMapper.builder().build())
  }
}
