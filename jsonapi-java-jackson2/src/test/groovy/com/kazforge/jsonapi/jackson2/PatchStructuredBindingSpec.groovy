package com.kazforge.jsonapi.jackson2

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import com.kazforge.jsonapi.fixtures.TestFixtureResources
import com.kazforge.jsonapi.fixtures.domainpatch.AddressPatch
import com.kazforge.jsonapi.fixtures.domainpatch.Article
import com.kazforge.jsonapi.fixtures.domainpatch.ArticlePatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithAddressPatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithBoxPatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithDirectPresentAddressPatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithMixedAddressPatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithOptionalAddress
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithOptionalAddressPatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithRawAddressPatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithTags
import com.kazforge.jsonapi.fixtures.domainpatch.BoxPatch
import com.kazforge.jsonapi.fixtures.domainpatch.MutableArticle
import com.kazforge.jsonapi.fixtures.domainpatch.PatchPresenceAddressPatchArticle
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.jackson.patch.PatchChange
import com.kazforge.jsonapi.jackson.patch.PatchCommand
import com.kazforge.jsonapi.jackson.patch.PatchPresence
import com.kazforge.jsonapi.jackson.patch.StructuredMember
import com.kazforge.jsonapi.jackson.patch.StructuredMemberState
import com.kazforge.jsonapi.jackson.patch.StructuredPatch
import spock.lang.Specification
import spock.lang.Unroll

class PatchStructuredBindingSpec extends Specification {

  @Unroll
  def "low-level structured binding #id"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/${resource}.json")

    when:
    def actual = reader.readValue(json, Article)

    then:
    actual == expected

    where:
    id | resource | expected
    "nested-partial" | "address-street-new-street" | patch(Article, "1", new PatchChange.AttributeChange("address", "address", structured(atomic("street", "New Street"))))
    "empty-object" | "address-empty-object" | patch(Article, "1", new PatchChange.AttributeChange("address", "address", structured()))
    "explicit-null" | "address-explicit-null" | patch(Article, "1", new PatchChange.AttributeChange("address", "address", null))
    "unknown-nested-skipped" | "address-bogus-and-street" | patch(Article, "1", new PatchChange.AttributeChange("address", "address", structured(atomic("street", "S"))))
  }

  def "low-level optional, container, generic, and javabean shapes"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())

    expect:
    reader.readValue(TestFixtureResources.readCorpusUtf8("patch/address-street-new-street.json"), ArticleWithOptionalAddress) ==
        patch(ArticleWithOptionalAddress, "1", new PatchChange.AttributeChange("address", "address", structured(atomic("street", "New Street"))))
    reader.readValue(TestFixtureResources.readCorpusUtf8("patch/tags-top-level.json"), ArticleWithTags) ==
        patch(ArticleWithTags, "1", new PatchChange.AttributeChange("tags", "tags", ["a", "b"]))
    reader.readValue(TestFixtureResources.readCorpusUtf8("patch/address-street.json"), MutableArticle) ==
        patch(MutableArticle, "1", new PatchChange.AttributeChange("address", "address", structured(atomic("street", "S"))))
  }

  @Unroll
  def "typed structured binding #id"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/${resource}.json")

    when:
    def actual = reader.readValue(json, ArticleWithAddressPatch)

    then:
    actual == expected

    where:
    id | resource | expected
    "nested-partial" | "address-street-new-street" | new ArticleWithAddressPatch("1", PatchPresence.present(new AddressPatch(PatchPresence.present("New Street"), PatchPresence.omitted())))
    "nested-empty-object" | "address-empty-object" | new ArticleWithAddressPatch("1", PatchPresence.present(new AddressPatch(PatchPresence.omitted(), PatchPresence.omitted())))
    "nested-explicit-null" | "address-explicit-null" | new ArticleWithAddressPatch("1", PatchPresence.present(null))
    "nested-omitted" | "identity-only" | new ArticleWithAddressPatch("1", PatchPresence.omitted())
  }

  def "typed optional, container, and generic nested shapes"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())

    expect:
    reader.readValue(TestFixtureResources.readCorpusUtf8("patch/address-street.json"), ArticleWithOptionalAddressPatch) ==
        new ArticleWithOptionalAddressPatch("1", PatchPresence.present(Optional.of(new AddressPatch(PatchPresence.present("S"), PatchPresence.omitted()))))
    reader.readValue(TestFixtureResources.readCorpusUtf8("patch/address-explicit-null.json"), ArticleWithOptionalAddressPatch) ==
        new ArticleWithOptionalAddressPatch("1", PatchPresence.present(Optional.empty()))
    reader.readValue(TestFixtureResources.readCorpusUtf8("patch/box-numbers.json"), ArticleWithBoxPatch) ==
        new ArticleWithBoxPatch("1", PatchPresence.present(new BoxPatch(PatchPresence.present([1, 2]))))
  }

  @Unroll
  def "typed invalid nested declarations fail #id"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/${resource}.json")

    when:
    reader.readValue(json, targetType)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == expectedDiagnostic

    where:
    id | resource | targetType | expectedDiagnostic
    "mixed-shape" | "address-street-city" | ArticleWithMixedAddressPatch | MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    "raw-shape" | "address-street-city" | ArticleWithRawAddressPatch | MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    "direct-present-shape" | "address-street-city" | ArticleWithDirectPresentAddressPatch | MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    "scalar-wire" | "address-scalar-wire" | ArticleWithAddressPatch | MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
  }

  def "typed unknown nested member fails with escaped location"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/address-unknown-member.json")

    when:
    reader.readValue(json, ArticleWithAddressPatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNKNOWN_PATCH_MEMBER
    ex.location().pointer() == "/attributes/address/bogus"
  }

  def "low-level presence-aware nested shape is rejected"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/address-street.json")

    when:
    reader.readValue(json, PatchPresenceAddressPatchArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
  }

  def "marker invariant survives caller naming strategy"() {
    given:
    def caller = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build()
    def reader = JsonApiJackson2.patchDtoReader(caller)
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'

    when:
    def patch = reader.readValue(json, ArticlePatch)

    then:
    patch.title() == PatchPresence.present("T")
  }

  private static PatchCommand patch(Class targetType, Object identity, PatchChange... changes) {
    return new PatchCommand(targetType, identity, Arrays.asList(changes))
  }

  private static StructuredPatch structured(StructuredMember... members) {
    return new StructuredPatch(Arrays.asList(members))
  }

  private static StructuredMember atomic(String name, Object value) {
    return new StructuredMember(name, name, new StructuredMemberState.Atomic(value))
  }
}
