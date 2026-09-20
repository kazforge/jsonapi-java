package com.kazforge.jsonapi.jackson3

import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.mapping.contract.BindingBackendContract
import com.kazforge.jsonapi.mapping.contract.BindingContractAdapter
import com.kazforge.jsonapi.mapping.internal.GenericDomainResourceBinder
import com.kazforge.jsonapi.jackson3.internal.Jackson3PrototypeBindingBackend
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

class Jackson3BindingBackendContractSpec extends Specification {

  def "existing public binder satisfies the neutral binding contract"() {
    given:
    def binder = JsonApiJackson3.resourceBinder(JsonMapper.builder().build())
    def adapter = new BindingContractAdapter() {
          @Override
          def <T> T fromResource(ResourceObject resource, Class<T> targetClass) {
            targetClass.cast(binder.fromResource(resource, targetClass))
          }
        }

    when:
    BindingBackendContract.verify(adapter)

    then:
    noExceptionThrown()
  }

  def "shared binding-domain binder satisfies the same contract"() {
    given:
    def binder = new GenericDomainResourceBinder<>(
        new Jackson3PrototypeBindingBackend(JsonMapper.builder().build()))
    def adapter = new BindingContractAdapter() {
          @Override
          def <T> T fromResource(ResourceObject resource, Class<T> targetClass) {
            binder.fromResource(resource, targetClass)
          }
        }

    when:
    BindingBackendContract.verify(adapter)

    then:
    noExceptionThrown()
  }
}
