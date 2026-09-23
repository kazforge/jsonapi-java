package com.kazforge.jsonapi.fixtures.contract

import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.core.model.Attributes
import com.kazforge.jsonapi.core.model.DocumentData
import com.kazforge.jsonapi.core.model.JsonApiDocument
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.Relationship
import com.kazforge.jsonapi.core.model.Relationships
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.fixtures.domainpatch.AddressPatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithAddressPatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithMapMeta
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithMapMetaPatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithMixedAddressPatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithOptionalAddress
import com.kazforge.jsonapi.fixtures.domainpatch.PatchPresenceAddressPatchArticle
import com.kazforge.jsonapi.patch.PatchChange
import com.kazforge.jsonapi.patch.PatchPresence
import com.kazforge.jsonapi.patch.StructuredMember
import com.kazforge.jsonapi.patch.StructuredMemberState
import com.kazforge.jsonapi.patch.StructuredPatch
import spock.lang.Specification

/**
 * Level-1 characterization contract for recursive structured PATCH semantics shared by the typed
 * {@code PatchPresence} DTO path ({@code readPatch}) and the low-level {@code PatchCommand} path
 * ({@code readCommand}): structured attributes, whole resource meta, relationship meta gated on
 * relationship data, nested omission versus explicit null versus supplied empty object, nested
 * unknown-member strictness on the typed path versus skip on the low-level path, and the
 * typed-only nature of presence-aware nested shapes. Concrete adapter subclasses supply the
 * configured runtime.
 */
abstract class StructuredPatchCharacterizationSpec extends Specification {

  protected abstract JsonApi api()

  def "projects typed structured attribute omission, explicit null, and supplied empty object"() {
    when:
    def omitted = api().patches().readPatch('{"data":{"type":"articles","id":"1"}}', ArticleWithAddressPatch)
    def partial = api().patches().readPatch(
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"street":"S"}}}}', ArticleWithAddressPatch)
    def explicitNull = api().patches().readPatch(
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"street":"S","city":null}}}}',
        ArticleWithAddressPatch)
    def empty = api().patches().readPatch(
        '{"data":{"type":"articles","id":"1","attributes":{"address":{}}}}', ArticleWithAddressPatch)

    then:
    omitted.address() == PatchPresence.omitted()
    partial.address() ==
        PatchPresence.present(new AddressPatch(PatchPresence.present("S"), PatchPresence.omitted()))
    explicitNull.address() ==
        PatchPresence.present(new AddressPatch(PatchPresence.present("S"), PatchPresence.present(null)))
    empty.address() ==
        PatchPresence.present(new AddressPatch(PatchPresence.omitted(), PatchPresence.omitted()))
  }

  def "projects low-level structured attribute omission, explicit null, and supplied empty object"() {
    when:
    def omitted = api().patches().readCommand('{"data":{"type":"articles","id":"1"}}', ArticleWithOptionalAddress)
    def partial = api().patches().readCommand(
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"street":"S"}}}}', ArticleWithOptionalAddress)
    def explicitNull = api().patches().readCommand(
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"street":"S","city":null}}}}',
        ArticleWithOptionalAddress)
    def empty = api().patches().readCommand(
        '{"data":{"type":"articles","id":"1","attributes":{"address":{}}}}', ArticleWithOptionalAddress)

    then:
    omitted.changes() == []
    partial.changes() == [
      new PatchChange.AttributeChange(
      "address", "address",
      new StructuredPatch([
        new StructuredMember("street", "street", new StructuredMemberState.Atomic("S"))
      ]))
    ]
    explicitNull.changes() == [
      new PatchChange.AttributeChange(
      "address", "address",
      new StructuredPatch([
        new StructuredMember("street", "street", new StructuredMemberState.Atomic("S")),
        new StructuredMember("city", "city", new StructuredMemberState.Atomic(null))
      ]))
    ]
    empty.changes() == [
      new PatchChange.AttributeChange("address", "address", new StructuredPatch([]))
    ]
  }

  def "rejects a typed nested unknown member at its escaped wire pointer"() {
    when:
    api().patches().readPatch(
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"street":"S","bogus":"x"}}}}',
        ArticleWithAddressPatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNKNOWN_PATCH_MEMBER
    ex.propertyPath() == "/attributes/address/bogus"
  }

  def "skips a low-level nested unknown member"() {
    when:
    def command = api().patches().readCommand(
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"street":"S","bogus":"x"}}}}',
        ArticleWithOptionalAddress)

    then:
    command.changes() == [
      new PatchChange.AttributeChange(
      "address", "address",
      new StructuredPatch([
        new StructuredMember("street", "street", new StructuredMemberState.Atomic("S"))
      ]))
    ]
  }

  def "rejects a mixed typed nested shape at the attribute pointer"() {
    when:
    api().patches().readPatch(
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"street":"S"}}}}',
        ArticleWithMixedAddressPatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    ex.propertyPath() == "/attributes/address"
  }

  def "rejects a low-level presence-aware nested shape at the attribute pointer"() {
    when:
    api().patches().readCommand(
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"street":"S"}}}}',
        PatchPresenceAddressPatchArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    ex.propertyPath() == "/attributes/address"
  }

  def "binds typed whole resource meta atomically"() {
    when:
    def supplied = api().patches().readPatch(
        '{"data":{"type":"articles","id":"1","meta":{"source":"cms"}}}', ArticleWithMapMetaPatch)
    def omitted = api().patches().readPatch('{"data":{"type":"articles","id":"1"}}', ArticleWithMapMetaPatch)

    then:
    supplied.meta() == PatchPresence.present([source: "cms"])
    omitted.meta() == PatchPresence.omitted()
  }

  def "binds typed relationship linkage and meta together and omits meta without relationship data"() {
    when:
    def together = api().patches().readPatch(
        '{"data":{"type":"articles","id":"1","relationships":{"author":{"data":' +
        '{"type":"people","id":"p1"},"meta":{"displayName":"A"}}}}}',
        ArticleWithMapMetaPatch)
    def dataLess =
        api().patches().bindPatch(
        singleResource(
        "articles",
        "1",
        null,
        null,
        Relationships.ofRelationships(
        [author: Relationship.metaOnly(Meta.of([displayName: "A"]))])),
        ArticleWithMapMetaPatch)

    then:
    together.author() == PatchPresence.present(ResourceIdentifier.of("people", "p1"))
    together.authorMeta() == PatchPresence.present([displayName: "A"])
    dataLess.author() == PatchPresence.omitted()
    dataLess.authorMeta() == PatchPresence.omitted()
  }

  def "projects low-level whole resource meta and relationship meta gated on relationship data"() {
    given:
    def withData =
        '{"data":{"type":"articles","id":"1","meta":{"source":"cms"},"relationships":' +
        '{"author":{"data":{"type":"people","id":"p1"},"meta":{"displayName":"A"}}}}}'

    when:
    def command = api().patches().readCommand(withData, ArticleWithMapMeta)
    def dataLessCommand =
        api().patches().bindCommand(
        singleResource(
        "articles",
        "1",
        null,
        null,
        Relationships.ofRelationships(
        [author: Relationship.metaOnly(Meta.of([displayName: "A"]))])),
        ArticleWithMapMeta)

    then:
    command.changes() == [
      new PatchChange.ResourceMetaChange("meta", "meta", [source: "cms"]),
      new PatchChange.RelationshipChange(
      "author", "author", ResourceIdentifier.of("people", "p1")),
      new PatchChange.RelationshipMetaChange("author", "authorMeta", [displayName: "A"])
    ]
    dataLessCommand.changes() == []
  }

  def "rejects a typed unknown supplied top-level attribute at its wire pointer"() {
    when:
    api().patches().readPatch(
        '{"data":{"type":"articles","id":"1","attributes":{"bogus":"x"}}}', ArticleWithAddressPatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNKNOWN_PATCH_MEMBER
    ex.propertyPath() == "/attributes/bogus"
  }

  def "rejects a missing typed identity before binding supplied members"() {
    when:
    api().patches().bindPatch(
        singleResource(
        "articles",
        null,
        null,
        Attributes.ofAttributes([address: [street: "S"]]),
        null),
        ArticleWithAddressPatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
    ex.propertyPath() == "/id"
  }

  private static JsonApiDocument singleResource(
      String type,
      String id,
      String lid,
      Attributes attributes,
      Relationships relationships) {
    JsonApiDocument.withData(
        new DocumentData.SingleResource(
        new ResourceObject(type, id, lid, attributes, relationships, null, null, Map.of())))
  }
}
