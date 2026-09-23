package com.kazforge.jsonapi.mapping.internal

import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.diagnostic.MappingLocation
import com.kazforge.jsonapi.internal.patch.PresenceMarker
import com.kazforge.jsonapi.patch.StructuredMember
import com.kazforge.jsonapi.patch.StructuredMemberState
import com.kazforge.jsonapi.patch.StructuredPatch
import spock.lang.Specification


/**
 * Neutral recursive structured-value binder semantics proven against a fake shape backend: typed
 * marker-tree assembly, low-level nested {@code StructuredPatch} assembly, null/empty/atomic
 * distinctions, unknown-member strictness versus skip, customization-forced atomic conversion, and
 * presence-aware low-level rejection.
 */
class MappingStructuredPatchBinderSpec extends Specification {

  private static final MappingLocation META = MappingLocation.of("meta")

  private MappingFakeStructuredShapeBackend backend
  private StructuredPatchBinder<String> binder

  def setup() {
    backend = new MappingFakeStructuredShapeBackend()
    binder = new StructuredPatchBinder<>(backend)
  }

  def "typed mode assembles present and omitted markers for a presence-aware shape"() {
    given:
    backend.defineShape("bean:AddressPatch", MappingFakeStructuredShapeBackend.member("street", "presence:s"), MappingFakeStructuredShapeBackend.member("city", "presence:s"))

    when:
    def value = binder.typedMemberValue([street: "S"], "presence:bean:AddressPatch", META, Object)

    then:
    value == [
      street: new PresenceMarker(true, "S"),
      city: new PresenceMarker(false, null)
    ]
  }

  def "typed mode returns explicit null and defers non-object wire values"() {
    given:
    backend.defineShape("bean:AddressPatch", MappingFakeStructuredShapeBackend.member("street", "presence:s"))

    expect:
    binder.typedMemberValue(null, "presence:bean:AddressPatch", META, Object) == null
    binder.typedMemberValue("atomic", "presence:bean:AddressPatch", META, Object) == "atomic"
  }

  def "typed mode rejects an unknown nested member at the accumulated pointer"() {
    given:
    backend.defineShape("bean:Inner", MappingFakeStructuredShapeBackend.member("street", "presence:s"))
    backend.defineShape("bean:Outer", MappingFakeStructuredShapeBackend.member("address", "presence:bean:Inner"))

    when:
    binder.typedMemberValue([address: [bogus: "x"]], "presence:bean:Outer", META, Object)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNKNOWN_PATCH_MEMBER
    ex.propertyPath() == "/meta/address/bogus"
  }

  def "typed mode rejects a mixed shape at the member pointer"() {
    given:
    backend.defineShape("bean:Mixed", MappingFakeStructuredShapeBackend.member("street", "presence:s"), MappingFakeStructuredShapeBackend.member("city", "s"))

    when:
    binder.typedMemberValue([street: "S"], "presence:bean:Mixed", META, Object)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    ex.location() == META
  }

  def "typed mode rejects wrapper customization on a nested shape member"() {
    given:
    backend.defineShape(
        "bean:Customized", MappingFakeStructuredShapeBackend.member("street", "presence:s"), MappingFakeStructuredShapeBackend.member("city", "presence:s", true))

    when:
    binder.typedMemberValue([street: "S"], "presence:bean:Customized", META, Object)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    ex.propertyPath() == "/meta/city"
  }

  def "typed mode rejects explicit null for a primitive member"() {
    when:
    binder.typedMemberValue(null, "presence:int", META, Object)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    ex.location() == META
  }

  def "low-level mode recurses supplied members in declaration order"() {
    given:
    backend.defineShape("bean:Address", MappingFakeStructuredShapeBackend.member("street", "s"), MappingFakeStructuredShapeBackend.member("city", "s"))

    expect:
    binder.lowLevelKind("bean:Address", [city: "C", street: "S"], false, META, Object) ==
    StructuredPatchBinder.LowLevelKind.RECURSE

    when:
    def patch = binder.bindLowLevelStructured([city: "C", street: "S"], "bean:Address", META, Object)

    then:
    patch == new StructuredPatch([
      new StructuredMember("street", "street", new StructuredMemberState.Atomic("S")),
      new StructuredMember("city", "city", new StructuredMemberState.Atomic("C"))
    ])
    backend.atomicCalls*.wireName == ["street", "city"]
  }

  def "low-level mode stays atomic for non-object wire values and customized members"() {
    given:
    backend.defineShape(
        "bean:Outer",
        MappingFakeStructuredShapeBackend.member("details", "bean:Details", false, true),
        MappingFakeStructuredShapeBackend.member("labels", "s"))
    backend.convertedValue("details", "converted")

    expect:
    binder.lowLevelKind("bean:Outer", [details: [name: "x"]], false, META, Object) ==
    StructuredPatchBinder.LowLevelKind.RECURSE

    when:
    def patch = binder.bindLowLevelStructured([details: [name: "x"], labels: "L"], "bean:Outer", META, Object)

    then:
    patch == new StructuredPatch([
      new StructuredMember("details", "details", new StructuredMemberState.Atomic("converted")),
      new StructuredMember("labels", "labels", new StructuredMemberState.Atomic("L"))
    ])
  }

  def "low-level mode skips unknown members and rejects presence-aware nested shapes"() {
    given:
    backend.defineShape("bean:Address", MappingFakeStructuredShapeBackend.member("street", "s"))
    backend.defineShape("bean:AddressPatch", MappingFakeStructuredShapeBackend.member("street", "presence:s"))

    expect:
    binder.bindLowLevelStructured([bogus: "x"], "bean:Address", META, Object) ==
    new StructuredPatch([])

    when:
    binder.lowLevelKind("presence:bean:AddressPatch", [street: "S"], false, META, Object)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE
    ex.location() == META
  }

  def "low-level mode rejects explicit null for a primitive nested member"() {
    given:
    backend.defineShape("bean:Dimensions", MappingFakeStructuredShapeBackend.member("width", "int"))

    when:
    binder.bindLowLevelStructured([width: null], "bean:Dimensions", META, Object)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    ex.propertyPath() == "/meta/width"
  }

  def "typedShape unwraps Optional and PatchPresence and only returns presence-aware shapes"() {
    given:
    backend.defineShape("bean:AddressPatch", MappingFakeStructuredShapeBackend.member("street", "presence:s"))
    backend.defineShape("bean:Address", MappingFakeStructuredShapeBackend.member("street", "s"))

    expect:
    binder.typedShape("bean:AddressPatch") != null
    binder.typedShape("presence:bean:AddressPatch") != null
    binder.typedShape("presence:optional:bean:AddressPatch") != null
    binder.typedShape("bean:Address") == null
  }
}
