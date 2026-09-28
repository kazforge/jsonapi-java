package com.kazforge.jsonapi.jackson2

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import com.kazforge.jsonapi.fixtures.TestFixtureResources
import com.kazforge.jsonapi.fixtures.domainpatch.AddressPatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticlePatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithAddressPatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithBoxPatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithDirectPresentAddressPatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithOptionalAddressPatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithRawAddressPatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithTags
import com.kazforge.jsonapi.fixtures.domainpatch.BoxPatch
import com.kazforge.jsonapi.fixtures.domainpatch.MutableArticle
import com.kazforge.jsonapi.jackson2.PatchStructureFixtures.SerializeCustomizedAddressPatchDto
import com.kazforge.jsonapi.jackson2.PatchStructureFixtures.ThrowingGeoPatchDto
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.patch.PatchChange
import com.kazforge.jsonapi.patch.PatchCommand
import com.kazforge.jsonapi.patch.PatchPresence
import com.kazforge.jsonapi.patch.StructuredMember
import com.kazforge.jsonapi.patch.StructuredMemberState
import com.kazforge.jsonapi.patch.StructuredPatch
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Jackson 2 structured PATCH binding: native container, generic, and JavaBean nested shapes plus
 * typed-orchestration seams for deep construction-failure pointer translation through resolved
 * presence-aware shapes and wrapper-level serialization customization rejection on nested shape
 * entry.
 */
class PatchStructuredBindingSpec extends Specification {

  def "low-level optional, container, generic, and javabean shapes"() {
    given:
    def reader = JsonApiJackson2.patchCommandReader(JsonMapper.builder().build())

    expect:
    reader.readValue(TestFixtureResources.readCorpusUtf8("patch/tags-top-level.json"), ArticleWithTags) ==
        patch(ArticleWithTags, "1", new PatchChange.AttributeChange("tags", "tags", ["a", "b"]))
    reader.readValue(TestFixtureResources.readCorpusUtf8("patch/address-street.json"), MutableArticle) ==
        patch(MutableArticle, "1", new PatchChange.AttributeChange("address", "address", structured(atomic("street", "S"))))
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
    "raw-shape" | "address-street-city" | ArticleWithRawAddressPatch | MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    "direct-present-shape" | "address-street-city" | ArticleWithDirectPresentAddressPatch | MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    "scalar-wire" | "address-scalar-wire" | ArticleWithAddressPatch | MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
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

  def "translates a deep construction failure to the nested wire pointer"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())

    when:
    reader.readValue(
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"street":"S","geo":{"lat":"1"}}}}}',
        ThrowingGeoPatchDto)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.MISSING_CREATOR_INPUT
    ex.propertyPath() == "/attributes/address/geo"
  }

  def "rejects wrapper-level serialization customization on a nested presence-aware member"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())

    when:
    reader.readValue(
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"street":"S","city":"C"}}}}',
        SerializeCustomizedAddressPatchDto)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    ex.propertyPath() == "/attributes/address/city"
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
