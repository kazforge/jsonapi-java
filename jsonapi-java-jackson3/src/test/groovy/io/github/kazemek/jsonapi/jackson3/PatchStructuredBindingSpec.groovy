package io.github.kazemek.jsonapi.jackson3

import io.github.kazemek.jsonapi.annotation.JsonApiAttribute
import io.github.kazemek.jsonapi.annotation.JsonApiId
import io.github.kazemek.jsonapi.annotation.JsonApiResource
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic
import io.github.kazemek.jsonapi.jackson.patch.PatchChange
import io.github.kazemek.jsonapi.jackson.patch.PatchPresence
import io.github.kazemek.jsonapi.jackson.patch.StructuredMember
import io.github.kazemek.jsonapi.jackson.patch.StructuredMemberState
import io.github.kazemek.jsonapi.jackson.patch.StructuredPatch
import io.github.kazemek.jsonapi.jackson3.PatchStructureFixtures.AddressWithLoudNoteArticle
import io.github.kazemek.jsonapi.jackson3.PatchStructureFixtures.CreatorCustomizedAddressPatch
import io.github.kazemek.jsonapi.jackson3.PatchStructureFixtures.SetterCustomizedAddressPatch
import io.github.kazemek.jsonapi.jackson3.PatchStructureFixtures.SetterSerializeCustomizedAddressPatch
import io.github.kazemek.jsonapi.jackson3.PatchStructureFixtures.SnakeAddress
import io.github.kazemek.jsonapi.jackson3.PatchStructureFixtures.SnakeAddressPatch
import io.github.kazemek.jsonapi.jackson3.PatchStructureFixtures.ThrowingAddressPatch
import io.github.kazemek.jsonapi.jackson3.PatchStructureFixtures.ThrowingArticlePatch
import io.github.kazemek.jsonapi.jackson3.PatchStructureFixtures.WrapperCustomizedAddressPatch
import io.github.kazemek.jsonapi.jackson3.StructuredRecursionFixtures.Details
import io.github.kazemek.jsonapi.jackson3.StructuredRecursionFixtures.ExtendedProfile
import io.github.kazemek.jsonapi.jackson3.StructuredRecursionFixtures.OuterWithCreatorCustomDetails
import io.github.kazemek.jsonapi.jackson3.StructuredRecursionFixtures.OuterWithSetterAsProfile
import io.github.kazemek.jsonapi.jackson3.StructuredRecursionFixtures.OuterWithSetterCustomDetails
import io.github.kazemek.jsonapi.fixtures.domainpatch.AddressPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.AddressWithContainersPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithAddressPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithBox
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithBoxPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithContainerAddress
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithContainerAddressPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.ArticleWithOptionalAddress
import io.github.kazemek.jsonapi.fixtures.domainpatch.BoxPatch
import spock.lang.Specification
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper

class PatchStructuredBindingSpec extends Specification {

  def "naming strategy applies to top-level and nested structured marker maps"() {
    given:
    def mapper = JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .build()
    def reader = JsonApiJackson3.patchDtoReader(mapper)
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"street_name":"S","city":"C"}}}}'

    when:
    def dto = reader.readValue(json, SnakeAddressPatchDto)

    then:
    dto.id == "1"
    dto.address == PatchPresence.present(
        new SnakeAddressPatch(PatchPresence.present("S"), PatchPresence.present("C")))
  }

  def "naming strategy nested unknown member reports the wire pointer"() {
    given:
    def mapper = JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .build()
    def reader = JsonApiJackson3.patchDtoReader(mapper)
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"street_name":"S","bogus":"x"}}}}'

    when:
    reader.readValue(json, SnakeAddressPatchDto)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNKNOWN_PATCH_MEMBER
    ex.propertyPath() == "/attributes/address/bogus"
  }

  def "unknown nested members are escaped as JSON Pointer segments"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())

    when:
    reader.readValue(
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"external/name":"x"}}}}',
        ArticleWithAddressPatch)

    then:
    def slash = thrown(JsonApiMappingException)
    slash.diagnostic() == MappingDiagnostic.UNKNOWN_PATCH_MEMBER
    // Attribute values are not namespace-validated, so pointer-sensitive characters reach the
    // diagnostic and must be RFC 6901-escaped per segment.
    slash.propertyPath() == "/attributes/address/external~1name"

    when:
    reader.readValue(
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"a~b":"y"}}}}',
        ArticleWithAddressPatch)

    then:
    def tilde = thrown(JsonApiMappingException)
    tilde.diagnostic() == MappingDiagnostic.UNKNOWN_PATCH_MEMBER
    // ~ escapes to ~0 first, so the escape character itself can never fake a later ~1.
    tilde.propertyPath() == "/attributes/address/a~0b"
  }

  def "low-level naming strategy carries both wire and logical nested names"() {
    given:
    def mapper = JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .build()
    def reader = JsonApiJackson3.patchCommandReader(mapper)
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"street_name":"S"}}}}'

    when:
    def command = reader.readValue(json, SnakeAddressArticle)

    then:
    command.changes() == [
      new PatchChange.AttributeChange(
      "address", "address",
      new StructuredPatch([
        new StructuredMember("street_name", "streetName", new StructuredMemberState.Atomic("S"))
      ]))
    ]
  }

  def "wrapper-level @JsonDeserialize on a nested presence-aware member is rejected lazily"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"street":"S","city":"C"}}}}'

    when:
    reader.readValue(json, WrapperCustomizedAddressPatchDto)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    ex.propertyPath() == "/attributes/address/city"
  }

  def "deep shape-translated construction failure reports the wire pointer"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"street":"S","geo":{"lat":"1"}}}}}'

    when:
    reader.readValue(json, ThrowingGeoPatchDto)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.MISSING_CREATOR_INPUT
    ex.propertyPath() == "/attributes/address/geo"
  }

  def "deep construction failure path is translated under a naming strategy"() {
    given:
    def mapper = JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .build()
    def reader = JsonApiJackson3.patchDtoReader(mapper)
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"street":"S","geo":{"lat":"1"}}}}}'

    when:
    reader.readValue(json, ThrowingGeoPatchDto)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.MISSING_CREATOR_INPUT
    ex.propertyPath() == "/attributes/address/geo"
  }

  def "naming strategy rejects a nested logical member name on the typed path"() {
    given:
    def mapper = JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .build()
    def reader = JsonApiJackson3.patchDtoReader(mapper)
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"streetName":"S","city":"C"}}}}'

    when:
    reader.readValue(json, SnakeAddressPatchDto)

    then: // streetName is the logical name, not a wire alias under SNAKE_CASE; only street_name binds
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNKNOWN_PATCH_MEMBER
    ex.propertyPath() == "/attributes/address/streetName"
  }

  def "naming strategy skips a nested logical member name on the low-level path"() {
    given:
    def mapper = JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .build()
    def reader = JsonApiJackson3.patchCommandReader(mapper)
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"streetName":"S"}}}}'

    when:
    def command = reader.readValue(json, SnakeAddressArticle)

    then: // streetName is not a wire alias; it is skipped as an unknown nested member
    command.changes() == [
      new PatchChange.AttributeChange(
      "address", "address",
      new StructuredPatch([]))
    ]
  }

  def "nested property-level @JsonDeserialize is honored while the surrounding bean still recurses"() {
    given:
    def reader = JsonApiJackson3.patchCommandReader(JsonMapper.builder().build())
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"street":"S","note":"n"}}}}'

    when:
    def command = reader.readValue(json, AddressWithLoudNoteArticle)

    then:
    command.changes() == [
      new PatchChange.AttributeChange(
      "address", "address",
      new StructuredPatch([
        new StructuredMember("note", "note", new StructuredMemberState.Atomic("N")),
        new StructuredMember("street", "street", new StructuredMemberState.Atomic("S"))
      ]))
    ]
  }

  def "nested property-level deserialization failure reports the nested wire pointer"() {
    given:
    def reader = JsonApiJackson3.patchCommandReader(JsonMapper.builder().build())
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"street":"S","note":{"bad":"shape"}}}}}'

    when:
    reader.readValue(json, AddressWithLoudNoteArticle)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    ex.propertyPath() == "/attributes/address/note"
  }

  def "low-level nested array member is a single frozen atomic replacement"() {
    given:
    def reader = JsonApiJackson3.patchCommandReader(JsonMapper.builder().build())
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"street":"S","initials":["A","B"]}}}}'

    when:
    def command = reader.readValue(
        json, ArticleWithContainerAddress)

    then: // the array is not recursed into elements; it is one atomic replacement value
    def patch = (StructuredPatch) command.changes()[0].value()
    patch.members().size() == 2
    patch.members()[0] == new StructuredMember("street", "street", new StructuredMemberState.Atomic("S"))
    def initials = patch.members()[1]
    initials.wireName() == "initials"
    initials.logicalName() == "initials"
    initials.state() instanceof StructuredMemberState.Atomic
    ((StructuredMemberState.Atomic) initials.state()).value() == ["A", "B"] as String[]
  }

  def "typed nested array member is a single atomic replacement"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"street":"S","initials":["A","B"]}}}}'

    when:
    def dto = reader.readValue(
        json, ArticleWithContainerAddressPatch)

    then: // the array is not recursed into elements; it is one atomic replacement value
    def shape = (AddressWithContainersPatch) ((PatchPresence.Present) dto.address()).value()
    shape.street() == PatchPresence.present("S")
    shape.aliases() == PatchPresence.omitted()
    shape.scores() == PatchPresence.omitted()
    shape.initials() instanceof PatchPresence.Present
    ((PatchPresence.Present) shape.initials()).value() == ["A", "B"] as String[]
  }

  def "low-level nested generic JavaType is preserved through atomic conversion"() {
    given:
    def reader = JsonApiJackson3.patchCommandReader(JsonMapper.builder().build())
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"box":{"numbers":["1","2"]}}}}'

    when:
    def command = reader.readValue(json, ArticleWithBox)

    then: // List<Integer> element type is retained; a raw List would have kept the String elements
    command.changes() == [
      new PatchChange.AttributeChange(
      "box", "box",
      new StructuredPatch([
        new StructuredMember(
        "numbers", "numbers", new StructuredMemberState.Atomic([1, 2]))
      ]))
    ]
  }

  def "typed nested generic JavaType is preserved through atomic conversion"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"box":{"numbers":["1","2"]}}}}'

    when:
    def dto = reader.readValue(json, ArticleWithBoxPatch)

    then: // List<Integer> element type is retained; a raw List would have kept the String elements
    def box = (BoxPatch) ((PatchPresence.Present) dto.box()).value()
    box.numbers() == PatchPresence.present([1, 2])
  }

  def "top-level construction failure with an empty path reports no location"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"T"}}}'

    when:
    reader.readValue(json, ThrowingArticlePatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.MISSING_CREATOR_INPUT
    // No Jackson member names on the failure path, so the location is absent — never "/" or "".
    ex.location() == null
    ex.propertyPath() == null
  }

  def "typed and low-level paths express the same nested presence for the same request"() {
    given:
    def typedReader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def lowLevelReader = JsonApiJackson3.patchCommandReader(JsonMapper.builder().build())
    def partial = '{"data":{"type":"articles","id":"1","attributes":{"address":{"street":"New Street"}}}}'
    def withNull = '{"data":{"type":"articles","id":"1","attributes":{"address":{"street":"S","city":null}}}}'
    def empty = '{"data":{"type":"articles","id":"1","attributes":{"address":{}}}}'

    when:
    def typedPartial = typedReader.readValue(partial, ArticleWithAddressPatch)
    def lowPartial = lowLevelReader.readValue(partial, ArticleWithOptionalAddress)
    def typedNull = typedReader.readValue(withNull, ArticleWithAddressPatch)
    def lowNull = lowLevelReader.readValue(withNull, ArticleWithOptionalAddress)
    def typedEmpty = typedReader.readValue(empty, ArticleWithAddressPatch)
    def lowEmpty = lowLevelReader.readValue(empty, ArticleWithOptionalAddress)

    then: // nested Present(value) <-> Atomic(value); nested Omitted <-> absent member
    ((PatchPresence.Present) typedPartial.address()).value() ==
        new AddressPatch(PatchPresence.present("New Street"), PatchPresence.omitted())
    ((StructuredPatch) lowPartial.changes()[0].value()).members() == [
      new StructuredMember("street", "street", new StructuredMemberState.Atomic("New Street"))
    ]
    then: // nested Present(null) <-> Atomic(null)
    ((PatchPresence.Present) typedNull.address()).value() ==
        new AddressPatch(PatchPresence.present("S"), PatchPresence.present(null))
    ((StructuredPatch) lowNull.changes()[0].value()).members() == [
      new StructuredMember("street", "street", new StructuredMemberState.Atomic("S")),
      new StructuredMember("city", "city", new StructuredMemberState.Atomic(null))
    ]
    then: // Present(empty object) <-> empty StructuredPatch
    ((PatchPresence.Present) typedEmpty.address()).value() ==
        new AddressPatch(PatchPresence.omitted(), PatchPresence.omitted())
    ((StructuredPatch) lowEmpty.changes()[0].value()).members().isEmpty()
  }

  def "typed nested setter-level @JsonDeserialize on a presence-aware member is rejected"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"street":"S","city":"C"}}}}'

    when:
    reader.readValue(json, SetterCustomizedAddressPatchDto)

    then: // wrapper-level @JsonDeserialize on the setter is deserialization-side customization
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    ex.propertyPath() == "/attributes/address/city"
  }

  def "typed nested creator-parameter @JsonDeserialize on a presence-aware member is rejected"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"street":"S","city":"C"}}}}'

    when:
    reader.readValue(json, CreatorCustomizedAddressPatchDto)

    then: // wrapper-level @JsonDeserialize on the creator parameter is deserialization-side
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    ex.propertyPath() == "/attributes/address/city"
  }

  def "low-level bean-valued setter @JsonDeserialize stays atomic and applies the deserializer"() {
    given:
    def reader = JsonApiJackson3.patchCommandReader(JsonMapper.builder().build())
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"outer":{"details":{"name":"x"}}}}}'

    when:
    def command = reader.readValue(json, OuterWithSetterCustomDetailsArticle)

    then: // the surrounding bean recurses but the customized details member is Atomic, not Structured
    command.changes() == [
      new PatchChange.AttributeChange(
      "outer", "outer",
      new StructuredPatch([
        new StructuredMember(
        "details", "details", new StructuredMemberState.Atomic(new Details("custom")))
      ]))
    ]
  }

  def "low-level bean-valued creator-parameter @JsonDeserialize stays atomic and applies the deserializer"() {
    given:
    def reader = JsonApiJackson3.patchCommandReader(JsonMapper.builder().build())
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"outer":{"details":{"name":"x"}}}}}'

    when:
    def command = reader.readValue(json, OuterWithCreatorCustomDetailsArticle)

    then:
    command.changes() == [
      new PatchChange.AttributeChange(
      "outer", "outer",
      new StructuredPatch([
        new StructuredMember(
        "details", "details", new StructuredMemberState.Atomic(new Details("custom")))
      ]))
    ]
  }

  def "low-level bean-valued setter @JsonDeserialize(as=...) stays atomic rather than recursing"() {
    given:
    def reader = JsonApiJackson3.patchCommandReader(JsonMapper.builder().build())
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"outer":{"profile":{"name":"N","email":"E"}}}}}'

    when: // the surrounding bean recurses but the as-refined profile member is Atomic, not Structured
    def command = reader.readValue(json, OuterWithSetterAsProfileArticle)

    then:
    command.changes() == [
      new PatchChange.AttributeChange(
      "outer", "outer",
      new StructuredPatch([
        new StructuredMember(
        "profile", "profile",
        new StructuredMemberState.Atomic(new ExtendedProfile("N", "E")))
      ]))
    ]
  }

  def "wrapper-level @JsonSerialize on a non-getter side is rejected for typed PatchPresence"() {
    given:
    def reader = JsonApiJackson3.patchDtoReader(JsonMapper.builder().build())
    def json =
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"street":"S","city":"C"}}}}'

    when: // @JsonSerialize on the setter is wrapper-level serialization customization on a
    // non-getter side; both members are inspected symmetrically for both directions
    reader.readValue(json, SetterSerializeCustomizedAddressPatchDto)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    ex.propertyPath() == "/attributes/address/city"
  }

  @JsonApiResource(type = "articles")
  static class SnakeAddressPatchDto {
    @JsonApiId String id
    @JsonApiAttribute PatchPresence<SnakeAddressPatch> address
  }

  @JsonApiResource(type = "articles")
  static class SnakeAddressArticle {
    @JsonApiId String id
    @JsonApiAttribute SnakeAddress address
  }

  @JsonApiResource(type = "articles")
  static class WrapperCustomizedAddressPatchDto {
    @JsonApiId String id
    @JsonApiAttribute PatchPresence<WrapperCustomizedAddressPatch> address
  }

  @JsonApiResource(type = "articles")
  static class ThrowingGeoPatchDto {
    @JsonApiId String id
    @JsonApiAttribute PatchPresence<ThrowingAddressPatch> address
  }

  @JsonApiResource(type = "articles")
  static class SetterCustomizedAddressPatchDto {
    @JsonApiId String id
    @JsonApiAttribute PatchPresence<SetterCustomizedAddressPatch> address
  }

  @JsonApiResource(type = "articles")
  static class CreatorCustomizedAddressPatchDto {
    @JsonApiId String id
    @JsonApiAttribute PatchPresence<CreatorCustomizedAddressPatch> address
  }

  @JsonApiResource(type = "articles")
  static class OuterWithSetterCustomDetailsArticle {
    @JsonApiId String id
    @JsonApiAttribute OuterWithSetterCustomDetails outer
  }

  @JsonApiResource(type = "articles")
  static class OuterWithCreatorCustomDetailsArticle {
    @JsonApiId String id
    @JsonApiAttribute OuterWithCreatorCustomDetails outer
  }

  @JsonApiResource(type = "articles")
  static class OuterWithSetterAsProfileArticle {
    @JsonApiId String id
    @JsonApiAttribute OuterWithSetterAsProfile outer
  }

  @JsonApiResource(type = "articles")
  static class SetterSerializeCustomizedAddressPatchDto {
    @JsonApiId String id
    @JsonApiAttribute PatchPresence<SetterSerializeCustomizedAddressPatch> address
  }
}
