package com.kazforge.jsonapi.jackson3

import com.kazforge.jsonapi.jackson3.WholeMetaTargetFixtures.ConcreteTypedMeta
import com.kazforge.jsonapi.jackson3.WholeMetaTargetFixtures.ConcreteTypedMetaArticle
import com.kazforge.jsonapi.jackson3.WholeMetaTargetFixtures.PolyMetaArticle
import com.kazforge.jsonapi.jackson3.WholeMetaTargetFixtures.SourceMeta
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

class PolymorphicWholeMetaSpec extends Specification {

  def "concrete root-polymorphic whole-meta POJO writes its discriminator and binds"() {
    given:
    def mapper = JsonMapper.builder().build()

    when:
    def resource = JsonApiJackson3.resourceMapper(mapper).toResource(
        new ConcreteTypedMetaArticle("1", new ConcreteTypedMeta("v")))
    def bound = JsonApiJackson3.resourceBinder(mapper).fromResource(resource, ConcreteTypedMetaArticle)

    then:
    resource.meta().members() == [kind: "concrete", value: "v"]
    bound.meta() == new ConcreteTypedMeta("v")
  }

  def "abstract polymorphic whole-meta base writes and reads the selected subtype"() {
    given:
    def mapper = JsonMapper.builder().build()

    when:
    def resource = JsonApiJackson3.resourceMapper(mapper).toResource(
        new PolyMetaArticle("1", new SourceMeta("cms", "n")))
    def bound = JsonApiJackson3.resourceBinder(mapper).fromResource(resource, PolyMetaArticle)

    then:
    resource.meta().members() == [kind: "source", source: "cms", note: "n"]
    bound.meta() == new SourceMeta("cms", "n")
  }
}
