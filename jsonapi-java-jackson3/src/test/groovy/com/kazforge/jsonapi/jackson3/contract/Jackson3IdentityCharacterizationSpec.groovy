package com.kazforge.jsonapi.jackson3.contract

import com.kazforge.jsonapi.fixtures.contract.IdentityCharacterizationSpec
import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.jackson3.JsonApiJackson3
import tools.jackson.databind.json.JsonMapper

/** Jackson 3 binding of the shared identity characterization contract. */
class Jackson3IdentityCharacterizationSpec extends IdentityCharacterizationSpec {

  @Override
  protected JsonApi api() {
    JsonApiJackson3.jsonApi(JsonMapper.builder().build())
  }
}
