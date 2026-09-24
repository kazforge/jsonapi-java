package com.kazforge.jsonapi.jackson2.internal.codec

import com.kazforge.jsonapi.core.aggregate.ValidationContext
import com.kazforge.jsonapi.core.validation.JsonApiValidationException
import com.kazforge.jsonapi.core.validation.LinksContext
import com.kazforge.jsonapi.core.validation.ValidationRuleCode
import com.kazforge.jsonapi.diagnostic.SourceLocation
import java.util.function.Supplier
import spock.lang.Specification

/**
 * Direct behavioral proof for the Jackson 2 adapter-local wire helpers: member classification,
 * pointer escaping and accumulation, and core-pointer relocation.
 */
class WireCodecHelpersSpec extends Specification {

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
}
