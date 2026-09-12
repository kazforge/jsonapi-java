package com.kazforge.jsonapi.jackson2

import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.jackson2.PolymorphicMetaFixtures.ConcreteTypedMeta
import com.kazforge.jsonapi.jackson2.PolymorphicMetaFixtures.ConcreteTypedMetaArticle
import com.kazforge.jsonapi.jackson2.PolymorphicMetaFixtures.PolyMetaArticle
import com.kazforge.jsonapi.jackson2.PolymorphicMetaFixtures.SourceMeta
import spock.lang.Specification
import com.fasterxml.jackson.databind.json.JsonMapper

// Jackson 2 mechanism probes for whole-meta: root TypeDeserializer decoration for concrete and
// abstract polymorphic meta targets must not disqualify an otherwise object-shaped whole-meta
// declaration. Major-neutral whole-meta write and fieldset semantics live in direct adapter-owned
// cases; typed-PATCH parts of the corresponding Jackson 3 spec are outside this increment.
class PolymorphicMetaSpec extends Specification {

  def "concrete root-polymorphic whole-meta POJO is a valid declaration and maps"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())
    def article = new ConcreteTypedMetaArticle("1", new ConcreteTypedMeta("v"))

    when:
    def resource = mapper.toResource(article)

    then:
    resource.meta() == Meta.of(["kind": "concrete", "value": "v"])
  }

  def "abstract polymorphic whole-meta base maps through the discriminator on write"() {
    given:
    def mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())
    def article = new PolyMetaArticle("1", new SourceMeta("cms", "n"))

    when:
    def resource = mapper.toResource(article)

    then:
    resource.meta() == Meta.of(["kind": "source", "source": "cms", "note": "n"])
  }
}
