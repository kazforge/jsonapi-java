package com.kazforge.jsonapi.jackson2.contract

import com.fasterxml.jackson.databind.json.JsonMapper
import com.kazforge.jsonapi.fixtures.contract.LinkageCharacterizationSpec
import com.kazforge.jsonapi.jackson.api.JsonApi
import com.kazforge.jsonapi.jackson2.JsonApiJackson2

/** Jackson 2 binding of the shared linkage characterization contract. */
class Jackson2LinkageCharacterizationSpec extends LinkageCharacterizationSpec {

  @Override
  protected JsonApi api() {
    JsonApiJackson2.jsonApi(JsonMapper.builder().build())
  }
}
