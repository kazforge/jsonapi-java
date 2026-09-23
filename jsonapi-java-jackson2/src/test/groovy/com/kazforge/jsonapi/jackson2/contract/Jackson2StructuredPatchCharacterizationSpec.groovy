package com.kazforge.jsonapi.jackson2.contract

import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.fixtures.contract.StructuredPatchCharacterizationSpec
import com.kazforge.jsonapi.jackson2.JsonApiJackson2
import com.fasterxml.jackson.databind.json.JsonMapper

/** Jackson 2 binding of the shared recursive structured PATCH characterization contract. */
class Jackson2StructuredPatchCharacterizationSpec extends StructuredPatchCharacterizationSpec {

  @Override
  protected JsonApi api() {
    JsonApiJackson2.jsonApi(JsonMapper.builder().build())
  }
}
