package io.github.kazemek.jsonapi.jackson3.internal

import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingLocation
import io.github.kazemek.jsonapi.jackson.internal.patch.PresenceMarker
import io.github.kazemek.jsonapi.jackson.patch.PatchPresence
import io.github.kazemek.jsonapi.jackson.patch.StructuredMember
import io.github.kazemek.jsonapi.jackson.patch.StructuredMemberState
import io.github.kazemek.jsonapi.jackson.patch.StructuredPatch
import io.github.kazemek.jsonapi.jackson3.StructuredRecursionFixtures.AddressWithLoudNote
import io.github.kazemek.jsonapi.jackson3.StructuredRecursionFixtures.Details
import io.github.kazemek.jsonapi.jackson3.StructuredRecursionFixtures.EmailContact
import io.github.kazemek.jsonapi.jackson3.StructuredRecursionFixtures.ExtendedProfile
import io.github.kazemek.jsonapi.jackson3.StructuredRecursionFixtures.OuterWithNullEmptyCity
import io.github.kazemek.jsonapi.jackson3.StructuredRecursionFixtures.OuterWithSetterAsProfile
import io.github.kazemek.jsonapi.jackson3.StructuredRecursionFixtures.OuterWithSetterCustomDetails
import io.github.kazemek.jsonapi.jackson3.StructuredRecursionFixtures.OuterWithTypedContact
import io.github.kazemek.jsonapi.fixtures.domainpatch.Address
import io.github.kazemek.jsonapi.fixtures.domainpatch.AddressPatch
import io.github.kazemek.jsonapi.fixtures.domainpatch.Dimensions
import io.github.kazemek.jsonapi.fixtures.domainpatch.MixedAddressPatch
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

/**
 * Drives the {@link StructuredValueBinder} engine directly from non-attribute starting pointers
 * (e.g. a structured {@code meta} location) with no {@code ResourceMapping} / {@code MappingProperty}
 * / {@code @JsonApiAttribute} / {@code AttributeChange} in scope, proving the ADR-014 reuse
 * boundary: the engine is location-neutral and outer-state-policy-free.
 */
class StructuredValueBinderSpec extends Specification {

  private static final MappingLocation META = MappingLocation.parse("/meta")

  private static JsonMapper mapper() {
    JsonMapper.builder().addModule(new PatchPresenceModule()).build()
  }

  def "typed engine binds a presence-aware shape from a non-attribute pointer"() {
    given:
    def binder = new StructuredValueBinder(mapper())
    def target = mapper().typeFactory.constructParametricType(PatchPresence, AddressPatch)

    when:
    def value = binder.typedMemberValue([street: "S"], target, META, AddressPatch)

    then:
    value instanceof Map
    def map = (Map) value
    map.keySet() == ["street", "city"] as Set
    map.street instanceof PresenceMarker
    map.street.present()
    map.street.value() == "S"
    map.city instanceof PresenceMarker
    !map.city.present()
  }

  def "typed engine produces an explicit-null value the caller policy may reject"() {
    given:
    def binder = new StructuredValueBinder(mapper())
    def target = mapper().typeFactory.constructParametricType(PatchPresence, AddressPatch)

    when:
    def value = binder.typedMemberValue(null, target, META, AddressPatch)

    then: // the engine is outer-state-policy-free; rejecting Present(null) is the caller's policy
    value == null
  }

  def "typed engine accumulates the supplied pointer for nested failures"() {
    given:
    def binder = new StructuredValueBinder(mapper())
    def target = mapper().typeFactory.constructParametricType(PatchPresence, AddressPatch)

    when:
    binder.typedMemberValue([bogus: "x"], target, META, AddressPatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNKNOWN_PATCH_MEMBER
    ex.propertyPath() == "/meta/bogus"
  }

  def "typed engine rejects a mixed shape at a non-attribute pointer"() {
    given:
    def binder = new StructuredValueBinder(mapper())
    def target = mapper().typeFactory.constructParametricType(
        PatchPresence, MixedAddressPatch)

    when:
    binder.typedMemberValue([street: "S", city: "C"], target, META, AddressPatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    ex.location() == META
  }

  def "typed engine defers atomic conversion against a presence-aware shape"() {
    given:
    def binder = new StructuredValueBinder(mapper())
    def target = mapper().typeFactory.constructParametricType(PatchPresence, AddressPatch)

    expect:
    binder.typedMemberValue("not-an-object", target, META, AddressPatch) == "not-an-object"
  }

  def "low-level engine binds an ordinary domain bean from a non-attribute pointer"() {
    given:
    def binder = new StructuredValueBinder(JsonMapper.builder().build())
    def declared = JsonMapper.builder().build().constructType(Address)

    expect:
    binder.lowLevelKind(declared, [street: "S"], null, null, META, Address) ==
    StructuredValueBinder.LowLevelKind.RECURSE

    when:
    def patch = binder.bindLowLevelStructured([street: "S"], declared, META, Address)

    then:
    patch == new StructuredPatch(
        [
          new StructuredMember("street", "street", new StructuredMemberState.Atomic("S"))
        ])
  }

  def "low-level engine reports nested failures at the supplied pointer"() {
    given:
    def binder = new StructuredValueBinder(JsonMapper.builder().build())
    def dimensions = JsonMapper.builder().build()
        .constructType(Dimensions)

    expect:
    binder.lowLevelKind(dimensions, [width: null], null, null, META, Dimensions) ==
    StructuredValueBinder.LowLevelKind.RECURSE

    when:
    binder.bindLowLevelStructured([width: null], dimensions, META, Dimensions)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    ex.propertyPath() == "/meta/width"
  }

  def "low-level engine applies property-scoped @JsonDeserialize from a non-attribute pointer"() {
    given:
    def binder = new StructuredValueBinder(JsonMapper.builder().build())
    def declared = JsonMapper.builder().build()
        .constructType(AddressWithLoudNote)

    when: // the same location-neutral machinery a structured meta mapping would reuse
    def patch = binder.bindLowLevelStructured(
        [note: "n"], declared, META, AddressWithLoudNote)

    then: // the property-scoped UpperCaseStringDeserializer runs, producing "N" not "n"
    patch == new StructuredPatch(
        [
          new StructuredMember("note", "note", new StructuredMemberState.Atomic("N"))
        ])
  }

  def "low-level engine keeps a setter-customized bean-valued member atomic from a non-attribute pointer"() {
    given:
    def binder = new StructuredValueBinder(JsonMapper.builder().build())
    def declared = JsonMapper.builder().build()
        .constructType(OuterWithSetterCustomDetails)

    when: // the details setter's @JsonDeserialize must make it atomic, not a recursed StructuredPatch
    def patch = binder.bindLowLevelStructured(
        [details: [name: "x"]],
        declared,
        META,
        OuterWithSetterCustomDetails)

    then:
    patch == new StructuredPatch(
        [
          new StructuredMember(
          "details",
          "details",
          new StructuredMemberState.Atomic(
          new Details("custom")))
        ])
  }

  def "low-level engine detects setter-level @JsonDeserialize(as=...) via the real property type"() {
    given:
    def binder = new StructuredValueBinder(JsonMapper.builder().build())
    def declared = JsonMapper.builder().build()
        .constructType(OuterWithSetterAsProfile)

    when: // the profile setter's @JsonDeserialize(as=...) refinement is detected through the
    // resolved property type (a setter AnnotatedMethod.getType() is void, which would make the
    // refinement check throw); the member stays Atomic with the refined deserializer applied
    def patch = binder.bindLowLevelStructured(
        [profile: [name: "N", email: "E"]],
        declared,
        META,
        OuterWithSetterAsProfile)

    then:
    patch == new StructuredPatch(
        [
          new StructuredMember(
          "profile",
          "profile",
          new StructuredMemberState.Atomic(
          new ExtendedProfile("N", "E")))
        ])
  }

  def "low-level engine routes nested explicit null through the property null provider"() {
    given:
    def binder = new StructuredValueBinder(JsonMapper.builder().build())
    def declared = JsonMapper.builder().build()
        .constructType(OuterWithNullEmptyCity)

    when: // the city setter's @JsonSetter(nulls = Nulls.AS_EMPTY) null provider yields "" for null
    def patch = binder.bindLowLevelStructured(
        [city: null], declared, META, OuterWithNullEmptyCity)

    then: // not the root String deserializer's null value (null)
    patch == new StructuredPatch(
        [
          new StructuredMember(
          "city", "city", new StructuredMemberState.Atomic(""))
        ])
  }

  def "low-level engine preserves a property-level TypeDeserializer for a polymorphic nested member"() {
    given:
    def binder = new StructuredValueBinder(JsonMapper.builder().build())
    def declared = JsonMapper.builder().build()
        .constructType(OuterWithTypedContact)

    when: // the polymorphic contact converts through the property's TypeDeserializer path
    def patch = binder.bindLowLevelStructured(
        [contact: [kind: "email", email: "a@b.c"]],
        declared,
        META,
        OuterWithTypedContact)

    then:
    patch == new StructuredPatch(
        [
          new StructuredMember(
          "contact",
          "contact",
          new StructuredMemberState.Atomic(
          new EmailContact("a@b.c")))
        ])
  }
}
