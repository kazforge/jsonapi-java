package com.kazforge.jsonapi.jackson2.contract

import com.fasterxml.jackson.databind.json.JsonMapper
import com.kazforge.jsonapi.fixtures.contract.PrimaryDataShapeCharacterizationSpec
import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.jackson2.JsonApiJackson2

/** Jackson 2 binding of the shared Level-1 primary-data shape characterization contract. */
class Jackson2PrimaryDataShapeCharacterizationSpec extends PrimaryDataShapeCharacterizationSpec {

  @Override
  protected JsonApi api() {
    JsonApiJackson2.jsonApi(JsonMapper.builder().build())
  }
}
