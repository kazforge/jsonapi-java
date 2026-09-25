package com.kazforge.jsonapi.jackson2.contract

import com.fasterxml.jackson.databind.json.JsonMapper
import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.fixtures.contract.DefinitionCharacterizationSpec
import com.kazforge.jsonapi.jackson2.JsonApiJackson2

/** Jackson 2 binding of the shared mapping-definition characterization contract. */
class Jackson2DefinitionCharacterizationSpec extends DefinitionCharacterizationSpec {

  @Override
  protected JsonApi api() {
    JsonApiJackson2.jsonApi(JsonMapper.builder().build())
  }
}
