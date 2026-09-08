package io.github.kazemek.jsonapi.jackson2

import com.fasterxml.jackson.databind.json.JsonMapper
import io.github.kazemek.jsonapi.core.validation.ValidationContext
import io.github.kazemek.jsonapi.jackson.mapping.IdentifierConverter
import spock.lang.Specification

class JsonApiJackson2PatchConstructionSpec extends Specification {

  def "patch command reader factories select documented defaults"() {
    given:
    def base = JsonMapper.builder().build()

    expect:
    JsonApiJackson2.patchCommandReader(base) != null
    JsonApiJackson2.patchCommandReader(base, ValidationContext.defaults()) != null
    JsonApiJackson2.patchCommandReader(base, ValidationContext.defaults(), IdentifierConverter.defaults()) != null
    JsonApiJackson2.patchCommandReader(base, ValidationContext.defaults(), IdentifierConverter.defaults(), Map.of()) != null
  }

  def "patch dto reader factories select documented defaults"() {
    given:
    def base = JsonMapper.builder().build()

    expect:
    JsonApiJackson2.patchDtoReader(base) != null
    JsonApiJackson2.patchDtoReader(base, ValidationContext.defaults()) != null
    JsonApiJackson2.patchDtoReader(base, ValidationContext.defaults(), IdentifierConverter.defaults()) != null
    JsonApiJackson2.patchDtoReader(base, ValidationContext.defaults(), IdentifierConverter.defaults(), Map.of()) != null
  }

  def "patch factories never mutate the caller mapper"() {
    given:
    def caller = JsonMapper.builder().build()
    def before = caller.registeredModuleIds

    when:
    JsonApiJackson2.patchCommandReader(caller)
    JsonApiJackson2.patchDtoReader(caller)

    then:
    caller.registeredModuleIds == before
  }
}
