package com.kazforge.jsonapi.jackson2.contract

import com.fasterxml.jackson.databind.json.JsonMapper
import com.kazforge.jsonapi.fixtures.contract.IdentityCharacterizationSpec
import com.kazforge.jsonapi.jackson.api.JsonApi
import com.kazforge.jsonapi.jackson2.JsonApiJackson2

/** Jackson 2 binding of the shared identity characterization contract. */
class Jackson2IdentityCharacterizationSpec extends IdentityCharacterizationSpec {

  @Override
  protected JsonApi api() {
    JsonApiJackson2.jsonApi(JsonMapper.builder().build())
  }
}
