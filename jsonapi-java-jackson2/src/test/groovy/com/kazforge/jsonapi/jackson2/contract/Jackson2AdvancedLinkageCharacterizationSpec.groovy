package com.kazforge.jsonapi.jackson2.contract

import com.fasterxml.jackson.databind.json.JsonMapper
import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.fixtures.contract.AdvancedLinkageCharacterizationSpec
import com.kazforge.jsonapi.jackson2.JsonApiJackson2

/** Jackson 2 binding of the shared advanced relationship-linkage write characterization contract. */
class Jackson2AdvancedLinkageCharacterizationSpec extends AdvancedLinkageCharacterizationSpec {

  @Override
  protected JsonApi api() {
    JsonApiJackson2.jsonApi(JsonMapper.builder().build())
  }

  @Override
  protected ResourceObject mapResource(Object value) {
    JsonApiJackson2.resourceMapper(JsonMapper.builder().build()).toResource(value)
  }
}
