package com.kazforge.jsonapi.jackson2.contract

import com.fasterxml.jackson.databind.json.JsonMapper
import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.fixtures.contract.BindingConstructionCharacterizationSpec
import com.kazforge.jsonapi.jackson2.JsonApiJackson2

/** Jackson 2 binding of the shared binding-construction characterization contract. */
class Jackson2BindingConstructionCharacterizationSpec extends BindingConstructionCharacterizationSpec {

  @Override
  protected JsonApi api() {
    JsonApiJackson2.jsonApi(JsonMapper.builder().build())
  }
}
