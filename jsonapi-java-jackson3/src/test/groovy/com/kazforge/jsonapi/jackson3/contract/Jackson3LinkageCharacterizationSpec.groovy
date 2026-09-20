package com.kazforge.jsonapi.jackson3.contract

import com.kazforge.jsonapi.fixtures.contract.LinkageCharacterizationSpec
import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.jackson3.JsonApiJackson3
import tools.jackson.databind.json.JsonMapper

/** Jackson 3 binding of the shared linkage characterization contract. */
class Jackson3LinkageCharacterizationSpec extends LinkageCharacterizationSpec {

  @Override
  protected JsonApi api() {
    JsonApiJackson3.jsonApi(JsonMapper.builder().build())
  }
}
