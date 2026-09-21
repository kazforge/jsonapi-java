package com.kazforge.jsonapi.internal

import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.core.validation.JsonApiValidationException
import com.kazforge.jsonapi.core.validation.LinksContext
import com.kazforge.jsonapi.core.aggregate.ValidationContext
import com.kazforge.jsonapi.core.validation.ValidationRuleCode
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.diagnostic.SourceLocation
import com.kazforge.jsonapi.internal.mapping.IdentifierMetaSupport
import com.kazforge.jsonapi.internal.mapping.PropertyRole
import com.kazforge.jsonapi.internal.mapping.ResourceTypeMatch
import com.kazforge.jsonapi.internal.patch.PresenceMarker
import com.kazforge.jsonapi.internal.wire.JsonPointerAccumulator
import com.kazforge.jsonapi.internal.wire.MemberClassifier
import com.kazforge.jsonapi.internal.wire.PointerEscapes
import com.kazforge.jsonapi.internal.wire.ReadLocationIndex
import com.kazforge.jsonapi.internal.wire.ValidationPointers
import java.util.function.Supplier
import spock.lang.Specification

class JacksonInternalHelpersSpec extends Specification {

  def "member classification keeps attribute and relationship pass-through rules distinct from links"() {
    expect:
    MemberClassifier.isAtMember('@extension')
    !MemberClassifier.isAtMember('extension')
    !MemberClassifier.isAtMember('')
    MemberClassifier.isNamespacedMember('ext:peer')
    !MemberClassifier.isNamespacedMember(':peer')
    !MemberClassifier.isNamespacedMember('peer')
    MemberClassifier.isPassThroughAttributeOrRelationship('@extension')
    MemberClassifier.isPassThroughAttributeOrRelationship('ext:peer')
    !MemberClassifier.isPassThroughAttributeOrRelationship('peer')
    MemberClassifier.isPassThroughLinkMember('@extension')
    !MemberClassifier.isPassThroughLinkMember('ext:peer')
  }

  def "retained structural members keep extension, profile, and at channels"() {
    given:
    def defaults = ValidationContext.defaults()
    def profile = new ValidationContext(
        defaults.documentUsage(),
        defaults.primaryDataContext(),
        defaults.allowedExtensionNamespaces(),
        defaults.allowedProfileUris(),
        Set.of('custom') as Set,
        defaults.sparseFieldsetLinkageExemptions(),
        defaults.relationshipPaginationHints(),
        defaults.expectedEndpointIdentity())

    expect:
    MemberClassifier.isRetainedStructuralMember('@note', defaults)
    MemberClassifier.isRetainedStructuralMember('ext:note', defaults)
    !MemberClassifier.isRetainedStructuralMember('bogus', defaults)
    !MemberClassifier.isRetainedStructuralMember('https://example.com/rel', defaults)
    !MemberClassifier.isRetainedStructuralMember('custom', defaults)
    MemberClassifier.isRetainedStructuralMember('custom', profile)
  }

  def "recognized link members respect context, extension, profile, and at channels"() {
    given:
    def defaults = ValidationContext.defaults()
    def profile = new ValidationContext(
        defaults.documentUsage(),
        defaults.primaryDataContext(),
        defaults.allowedExtensionNamespaces(),
        defaults.allowedProfileUris(),
        Set.of('custom') as Set,
        defaults.sparseFieldsetLinkageExemptions(),
        defaults.relationshipPaginationHints(),
        defaults.expectedEndpointIdentity())

    expect:
    MemberClassifier.isRecognizedLinkMember('@note', defaults, LinksContext.TOP_LEVEL)
    MemberClassifier.isRecognizedLinkMember('ext:custom', defaults, LinksContext.TOP_LEVEL)
    MemberClassifier.isRecognizedLinkMember('self', defaults, LinksContext.TOP_LEVEL)
    !MemberClassifier.isRecognizedLinkMember('bogus', defaults, LinksContext.TOP_LEVEL)
    !MemberClassifier.isRecognizedLinkMember('https://example.com/rel', defaults, LinksContext.TOP_LEVEL)
    !MemberClassifier.isRecognizedLinkMember('custom', defaults, LinksContext.TOP_LEVEL)
    MemberClassifier.isRecognizedLinkMember('custom', profile, LinksContext.TOP_LEVEL)
    !MemberClassifier.isRecognizedLinkMember('about', defaults, LinksContext.TOP_LEVEL)
    MemberClassifier.isRecognizedLinkMember('about', defaults, LinksContext.ERROR)
    !MemberClassifier.isRecognizedLinkMember('self', defaults, LinksContext.ERROR)
  }

  def "pointer escaping is reversible for RFC 6901 segments"() {
    expect:
    PointerEscapes.escape('a~/b') == 'a~0~1b'
    PointerEscapes.unescape('a~0~1b') == 'a~/b'
  }

  def "pointer accumulation records escaped paths and first locations"() {
    given:
    def locations = new ReadLocationIndex()
    def pointer = new JsonPointerAccumulator(locations)
    def root = new SourceLocation(1, 1, 0L, 0L)
    def nested = new SourceLocation(2, 3, 8L, 8L)
    def later = new SourceLocation(9, 9, 99L, 99L)

    when:
    pointer.capture(root)
    pointer.push('a/b~c')
    pointer.pushIndex(2)
    pointer.capture(nested)
    pointer.capture(later)

    then:
    pointer.path() == '/a~1b~0c/2'
    locations.resolve('/a~1b~0c/2') == nested
    locations.resolve('/a~1b~0c/2/name') == nested
    locations.resolve('/missing') == root

    when:
    pointer.pop()
    pointer.pop()
    pointer.pop()
    pointer.pop()

    then:
    pointer.path() == ''
    locations.resolve('') == root
    new ReadLocationIndex().resolve('/missing') == SourceLocation.UNKNOWN
  }

  def "validation pointers relocate core roots and preserve unmatched failures"() {
    given:
    def nestedFailure = new JsonApiValidationException(
        ValidationRuleCode.INVALID_MEMBER_NAME, '/attributes/a~1b', 'bad member')
    def rootFailure = new JsonApiValidationException(
        ValidationRuleCode.MISSING_RESOURCE_ID, '/data', 'missing id')

    when:
    def value =
        ValidationPointers.construct('/data/0', '/attributes', ({ 'value' } as Supplier<Object>))

    then:
    value == 'value'
    ValidationPointers.relocate(nestedFailure, '/data/0', '/other').jsonPointer() == '/data/0'
    ValidationPointers.relocate(nestedFailure, '', '/attributes').is(nestedFailure)
    ValidationPointers.relocate(nestedFailure, '/data/0', '').is(nestedFailure)
    ValidationPointers.join('/data/0', '') == '/data/0'
    ValidationPointers.join('/data/0', '/a~1b/c~0d') == '/data/0/a~1b/c~0d'
    ValidationPointers.join('/data/0', 'a~1b') == '/data/0/a~1b'
    ValidationPointers.forCore([value: null]).value == null

    when:
    ValidationPointers.construct(
        '/data/0', '/attributes', ({ throw nestedFailure } as Supplier<Object>))

    then:
    def relocated = thrown(JsonApiValidationException)
    relocated.jsonPointer() == '/data/0/a~1b'
    relocated.ruleCode() == ValidationRuleCode.INVALID_MEMBER_NAME
    relocated.message == 'bad member'

    when:
    ValidationPointers.construct('/data/0', '/data', ({ throw rootFailure } as Supplier<Object>))

    then:
    def rootRelocated = thrown(JsonApiValidationException)
    rootRelocated.jsonPointer() == '/data/0'
  }

  def "resource type matching reports the neutral type location"() {
    given:
    def resource = ResourceObject.of('people', '1')

    when:
    ResourceTypeMatch.requireMatching('people', resource, String)

    then:
    noExceptionThrown()

    when:
    ResourceTypeMatch.requireMatching('articles', resource, String)

    then:
    def exception = thrown(RuntimeException)
    exception.diagnostic() == MappingDiagnostic.RESOURCE_TYPE_MISMATCH
    exception.resourceClass() == String
    exception.propertyPath() == '/type'
  }

  def "identifier meta support preserves the documented copy distinctions and locations"() {
    given:
    def originalMeta = Meta.of([source: 'linkage'])
    def replacementMeta = Meta.of([source: 'replacement'])
    def identifier = new ResourceIdentifier(
        'people', '1', 'local-1', originalMeta, ['ext:member': 'value'])

    expect:
    IdentifierMetaSupport.identifierMetaLocation('author').pointer() ==
        '/relationships/author/data/meta'
    IdentifierMetaSupport.identifierMetaLocation('comments', 2).pointer() ==
        '/relationships/comments/data/2/meta'

    def linkageCopy = IdentifierMetaSupport.copyLinkageIdentifier(identifier)
    linkageCopy.type() == 'people'
    linkageCopy.id() == '1'
    linkageCopy.lid() == 'local-1'
    linkageCopy.meta() == originalMeta
    linkageCopy.additionalMembers().isEmpty()

    def overlaid = IdentifierMetaSupport.withMeta(identifier, replacementMeta)
    overlaid.meta() == replacementMeta
    overlaid.additionalMembers() == ['ext:member': 'value']
  }

  def "mapping roles and supplied presence state remain neutral values"() {
    expect:
    PropertyRole.values()*.name() == [
      'ID',
      'LOCAL_ID',
      'ATTRIBUTE',
      'RELATIONSHIP',
      'RESOURCE_META',
      'RELATIONSHIP_META'
    ]
    new PresenceMarker(false, null) == new PresenceMarker(false, null)
    new PresenceMarker(true, 'value').present()
    new PresenceMarker(true, 'value').value() == 'value'
  }
}
