package com.kazforge.jsonapi.mapping.internal

import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import spock.lang.Specification

/**
 * Direct behavioral proof for the neutral mapping helpers shared by the mapping orchestration and
 * both adapters' cooperation surfaces: identifier-meta locations and copies, resource-type
 * matching, and supplied PATCH presence state.
 */
class MappingInternalHelpersSpec extends Specification {

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

  def "supplied presence state remains a neutral value"() {
    expect:
    new PresenceMarker(false, null) == new PresenceMarker(false, null)
    new PresenceMarker(true, 'value').present()
    new PresenceMarker(true, 'value').value() == 'value'
  }
}
