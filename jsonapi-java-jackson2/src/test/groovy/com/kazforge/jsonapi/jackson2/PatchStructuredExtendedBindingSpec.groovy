package com.kazforge.jsonapi.jackson2

import com.fasterxml.jackson.databind.json.JsonMapper
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleMeta
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithOptionalMetaPatch
import com.kazforge.jsonapi.fixtures.domainpatch.WholeMetaTargetFixtures
import com.kazforge.jsonapi.jackson2.PatchStructureFixtures.SerializeCustomizedAddressPatchDto
import com.kazforge.jsonapi.jackson2.PatchStructureFixtures.ThrowingGeoPatchDto
import com.kazforge.jsonapi.patch.PatchPresence
import spock.lang.Specification

/**
 * Jackson 2 regressions for the shared typed PATCH orchestration seams: {@code Optional}-wrapped
 * whole meta, nested-presence meta declaration rejection, deep construction-failure pointer
 * translation through resolved presence-aware shapes, and wrapper-level serialization
 * customization rejection on nested shape entry.
 */
class PatchStructuredExtendedBindingSpec extends Specification {

  def "binds optional typed resource meta and omits it"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())

    when:
    def supplied = reader.readValue(
        '{"data":{"type":"articles","id":"1","meta":{"source":"cms","note":"n"}}}',
        ArticleWithOptionalMetaPatch)
    def omitted = reader.readValue('{"data":{"type":"articles","id":"1"}}', ArticleWithOptionalMetaPatch)

    then:
    supplied.meta() == PatchPresence.present(Optional.of(new ArticleMeta("cms", "n")))
    omitted.meta() == PatchPresence.omitted()
  }

  def "rejects a nested-presence meta declaration at the meta pointer"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())

    when:
    reader.readValue(
        '{"data":{"type":"articles","id":"1","meta":{"source":"cms"}}}',
        WholeMetaTargetFixtures.NestedPresenceMetaPatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_META_TARGET
    ex.propertyPath() == "/meta"
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
}
