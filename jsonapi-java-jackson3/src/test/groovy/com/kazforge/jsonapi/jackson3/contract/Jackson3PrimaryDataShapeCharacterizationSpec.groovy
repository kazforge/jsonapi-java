package com.kazforge.jsonapi.jackson3.contract

import com.kazforge.jsonapi.fixtures.contract.PrimaryDataShapeCharacterizationSpec
import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.jackson3.JsonApiJackson3
import tools.jackson.databind.json.JsonMapper

/** Jackson 3 binding of the shared Level-1 primary-data shape characterization contract. */
class Jackson3PrimaryDataShapeCharacterizationSpec extends PrimaryDataShapeCharacterizationSpec {

  @Override
  protected JsonApi api() {
    JsonApiJackson3.jsonApi(JsonMapper.builder().build())
  }
}
