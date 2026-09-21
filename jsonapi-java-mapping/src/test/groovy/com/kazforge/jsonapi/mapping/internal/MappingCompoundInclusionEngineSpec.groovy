package com.kazforge.jsonapi.mapping.internal

import com.kazforge.jsonapi.core.model.Attributes
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.core.model.ResourceIdentity
import com.kazforge.jsonapi.core.model.ResourceObject
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.representation.IncludePolicy
import com.kazforge.jsonapi.representation.RelationshipAllowance
import com.kazforge.jsonapi.representation.RepresentationPolicy
import com.kazforge.jsonapi.representation.RepresentationSelection
import spock.lang.Specification

class MappingCompoundInclusionEngineSpec extends Specification {

  private final MappingFakeInclusionBackend backend = new MappingFakeInclusionBackend()
  private final CompoundInclusionEngine<String> engine = new CompoundInclusionEngine<>(backend)

  def "absent include request returns a null included list"() {
    when:
    def result = engine.collectIncluded([], [], [], null,
    new EffectiveRepresentation(RepresentationSelection.none(), RepresentationPolicy.defaults()))

    then:
    result.included() == null
    result.sparseFieldsetLinkageExemptions() == [] as Set
  }

  def "explicit empty include request returns an empty included list"() {
    given:
    def selection = RepresentationSelection.builder().includeRequested().build()

    when:
    def result = engine.collectIncluded([], [], [], null,
    new EffectiveRepresentation(selection, RepresentationPolicy.defaults()))

    then:
    result.included() == []
  }

  def "rejects a missing backend capability bridge"() {
    when:
    new CompoundInclusionEngine<>(null)

    then:
    thrown(NullPointerException)
  }

  def "rejects mismatched primary snapshot sizes"() {
    when:
    engine.collectIncluded(['a'], [], [], null,
    new EffectiveRepresentation(RepresentationSelection.none(), RepresentationPolicy.defaults()))

    then:
    thrown(IllegalArgumentException)
  }

  def "validates include paths against the supplied empty-collection type"() {
    given:
    backend.rawClasses['articles'] = String

    when:
    engine.collectIncluded([], [], [], 'articles', representation(['author']))

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.INVALID_INCLUDE_PATH
    exception.resourceClass() == String
  }

  def "validates each distinct primary type and reports the failing owner"() {
    given:
    backend.rawClasses['articles'] = String
    backend.rawClasses['people'] = Integer
    backend.relationships['articles'] = [author: 'people']

    when:
    engine.collectIncluded(['a1', 'p1'], ['articles', 'people'],
    [
      primary('articles', '1'),
      primary('people', '1')
    ], null, representation(['author']))

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.INVALID_INCLUDE_PATH
    exception.resourceClass() == Integer
    exception.message.contains("Unknown relationship 'author' on people")
  }

  def "reports a depth failure with the first distinct primary type"() {
    given:
    backend.rawClasses['articles'] = String

    when:
    engine.collectIncluded(['a1'], ['articles'], [primary('articles', '1')], null,
    representation(['author'], IncludePolicy.allowAll(), [:], 0))

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.INCLUDE_DEPTH_EXCEEDED
    exception.resourceClass() == String
    exception.propertyPath() == null
    exception.message.contains('maxIncludeDepth 0: author')
  }

  def "reports a depth failure without a resource class when no primary type is available"() {
    when:
    engine.collectIncluded([], [], [], null,
    representation(['author'], IncludePolicy.allowAll(), [:], 0))

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.INCLUDE_DEPTH_EXCEEDED
    exception.resourceClass() == null
  }

  def "reports an unknown relationship with the owner raw class and include path"() {
    given:
    backend.rawClasses['articles'] = String

    when:
    engine.collectIncluded(['a1'], ['articles'], [primary('articles', '1')], null,
    representation(['author']))

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.INVALID_INCLUDE_PATH
    exception.resourceClass() == String
    exception.propertyPath() == null
    exception.message == "Unknown relationship 'author' on articles in include path 'author'"
  }

  def "prefers an unknown-relationship failure over an include policy denial"() {
    given:
    backend.rawClasses['articles'] = String

    when:
    engine.collectIncluded(['a1'], ['articles'], [primary('articles', '1')], null,
    representation(['author'], IncludePolicy.denyAll()))

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.INVALID_INCLUDE_PATH
  }

  def "reports a denied relationship with the owner raw class"() {
    given:
    backend.rawClasses['articles'] = String
    backend.relationships['articles'] = [author: 'people']

    when:
    engine.collectIncluded(['a1'], ['articles'], [primary('articles', '1')], null,
    representation(['author'], IncludePolicy.denyAll()))

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.DENIED_RELATIONSHIP_INCLUDE
    exception.resourceClass() == String
    exception.message == "Include denied for articles.author in include path 'author'"
  }

  def "surfaces native related-type failures with the dotted include path"() {
    given:
    backend.relationships['articles'] = [comments: 'comments']
    backend.unresolvedRelatedTypes << 'comments'

    when:
    engine.collectIncluded(['a1'], ['articles'], [primary('articles', '1')], null,
    representation(['comments']))

    then:
    backend.relatedTypePaths == ['comments']
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_COLLECTION_TYPE
  }

  def "decides policy before native related-type resolution"() {
    given:
    backend.relationships['articles'] = [author: 'people']
    backend.unresolvedRelatedTypes << 'author'

    when:
    engine.collectIncluded(['a1'], ['articles'], [primary('articles', '1')], null,
    representation(['author'], IncludePolicy.denyAll()))

    then:
    thrown(JsonApiMappingException)
    backend.relatedTypePaths.isEmpty()
  }

  def "emits intermediate resources before resources reached through them"() {
    given:
    nestedCommentBackend()

    when:
    def result = engine.collectIncluded(['a1'], ['articles'], [primary('articles', '1')], null,
    representation(['comments.author']))

    then:
    result.included()*.type() == [
      'comments',
      'comments',
      'people',
      'people'
    ]
    result.included()*.id() == ['c1', 'c2', 'p1', 'p2']
    result.sparseFieldsetLinkageExemptions().isEmpty()
  }

  def "prefix-overlapping include paths emit each identity once"() {
    given:
    nestedCommentBackend()

    when:
    def result = engine.collectIncluded(['a1'], ['articles'], [primary('articles', '1')], null,
    representation(['comments', 'comments.author']))

    then:
    result.included()*.id() == ['c1', 'c2', 'p1', 'p2']
  }

  def "suppresses a related occurrence matching the primary id alias and keeps traversing"() {
    given:
    backend.relationships['articles'] = [self: 'articles', comments: 'comments']
    backend.relationshipValues('articles', 'self', 'a1')
    backend.relationshipValues('articles', 'comments', 'c1')
    backend.identifiers['a1'] = ResourceIdentifier.of('articles', '1')
    backend.identifiers['c1'] = ResourceIdentifier.of('comments', 'c1')
    backend.rendered['c1'] = ResourceObject.of('comments', 'c1')

    when:
    def result = engine.collectIncluded(['a1'], ['articles'], [primary('articles', '1')], null,
    representation(['self.comments']))

    then:
    result.included()*.id() == ['c1']
  }

  def "suppresses a related occurrence matching the primary local-id alias"() {
    given:
    backend.relationships['articles'] = [related: 'articles']
    backend.relationshipValues('articles', 'related', 'other')
    backend.identifiers['other'] = ResourceIdentifier.withLid('articles', 'l1')

    when:
    def result = engine.collectIncluded(['a1'], ['articles'], [
      primary('articles', '1', 'l1')
    ], null,
    representation(['related']))

    then:
    result.included() == []
  }

  def "visits a shared intermediate occurrence at the same traversal position once"() {
    given:
    backend.relationships['articles'] = [comments: 'comments']
    backend.relationships['comments'] = [author: 'people']
    backend.relationships['people'] = [related: 'people']
    backend.relationshipValues('articles', 'comments', 'c1', 'c2')
    backend.relationshipValues('comments', 'author', 'p1')
    backend.relationshipValues('people', 'related', 'p2')
    backend.identifiers['c1'] = ResourceIdentifier.of('comments', 'c1')
    backend.identifiers['c2'] = ResourceIdentifier.of('comments', 'c2')
    backend.identifiers['p1'] = ResourceIdentifier.of('people', 'p1')
    backend.identifiers['p2'] = ResourceIdentifier.of('people', 'p2')
    backend.rendered['c1'] = ResourceObject.of('comments', 'c1')
    backend.rendered['c2'] = ResourceObject.of('comments', 'c2')
    backend.rendered['p1'] = ResourceObject.of('people', 'p1')
    backend.rendered['p2'] = ResourceObject.of('people', 'p2')

    when:
    def result = engine.collectIncluded(['a1'], ['articles'], [primary('articles', '1')], null,
    representation(['comments.author.related']))

    then:
    result.included()*.id() == ['c1', 'c2', 'p1', 'p2']
    // The second comment reaches the already-visited author at segment one; the visit key stops
    // traversal there, so the author's downstream resource renders once.
    backend.renderCounts['p2'] == 1
  }

  def "stops a cyclic path at the primary-suppressed owner"() {
    given:
    backend.relationships['nodes'] = [child: 'nodes']
    backend.domainRelationshipValues('n1', 'child', 'n2')
    backend.domainRelationshipValues('n2', 'child', 'n1')
    backend.identifiers['n1'] = ResourceIdentifier.of('nodes', '1')
    backend.identifiers['n2'] = ResourceIdentifier.of('nodes', '2')
    backend.rendered['n1'] = ResourceObject.of('nodes', '1')
    backend.rendered['n2'] = ResourceObject.of('nodes', '2')

    when:
    def result = engine.collectIncluded(['n1'], ['nodes'], [primary('nodes', '1')], null,
    representation(['child.child.child']))

    then:
    result.included()*.id() == ['2']
  }

  def "includes an identity-less primary root only when create-request authoring allows it"() {
    given:
    backend.relationships['articles'] = [author: 'people']
    backend.relationships['people'] = [related: 'people']
    backend.relationshipValues('articles', 'author', 'p1')
    backend.relationshipValues('people', 'related', 'p2')
    backend.identifiers['p1'] = ResourceIdentifier.of('people', 'p1')
    backend.identifiers['p2'] = ResourceIdentifier.of('people', 'p2')
    backend.rendered['p1'] = ResourceObject.of('people', 'p1')
    backend.rendered['p2'] = ResourceObject.of('people', 'p2')
    backend.identityLess << 'a1'

    when:
    def lenient = engine.collectIncluded(['a1'], ['articles'], [primary('articles', '1')], null,
    representation(['author.related']), true)

    then:
    lenient.included()*.id() == ['p1', 'p2']

    when:
    backend.identityLess.clear()
    def identityBearing = engine.collectIncluded(['a1'], ['articles'],
    [primary('articles', '1')], null, representation(['author.related']), true)

    then:
    identityBearing.included()*.id() == ['p1', 'p2']
  }

  def "rejects a runtime relationship unknown on the effective owner type"() {
    given:
    backend.rawClasses['moderated-comments'] = Integer
    backend.relationships['articles'] = [comments: 'base-comments']
    backend.relationships['base-comments'] = [author: 'people']
    backend.relationshipValues('articles', 'comments', 'mc1')
    backend.identifiers['mc1'] = ResourceIdentifier.of('moderated-comments', '5')
    backend.effectiveTypes['mc1'] = 'moderated-comments'
    backend.rendered['mc1'] = ResourceObject.of('moderated-comments', '5')

    when:
    engine.collectIncluded(['a1'], ['articles'], [primary('articles', '1')], null,
    representation(['comments.author']))

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.INVALID_INCLUDE_PATH
    exception.resourceClass() == Integer
    exception.message.contains("Unknown relationship 'author' on moderated-comments")
  }

  def "denies an include for the runtime effective owner type"() {
    given:
    backend.relationships['articles'] = [comments: 'base-comments']
    backend.relationships['base-comments'] = [author: 'people']
    backend.relationships['moderated-comments'] = [author: 'people']
    backend.relationshipValues('articles', 'comments', 'mc1')
    backend.identifiers['mc1'] = ResourceIdentifier.of('moderated-comments', '5')
    backend.effectiveTypes['mc1'] = 'moderated-comments'
    backend.rendered['mc1'] = ResourceObject.of('moderated-comments', '5')
    def policy = IncludePolicy.allowing([
      RelationshipAllowance.of('articles', 'comments'),
      RelationshipAllowance.of('base-comments', 'author')
    ] as Set)

    when:
    engine.collectIncluded(['a1'], ['articles'], [primary('articles', '1')], null,
    representation(['comments.author'], policy))

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.DENIED_RELATIONSHIP_INCLUDE
    exception.resourceClass() == String
    exception.message.contains('Include denied for moderated-comments.author')
  }

  def "records a linkage exemption for a fieldset-omitted relationship"() {
    given:
    backend.relationships['articles'] = [author: 'people']
    backend.relationshipValues('articles', 'author', 'p1')
    backend.identifiers['p1'] = new ResourceIdentifier('people', 'p1', 'pl1', null, Map.of())
    backend.rendered['p1'] = ResourceObject.of('people', 'p1')

    when:
    def result = engine.collectIncluded(['a1'], ['articles'], [primary('articles', '1')], null,
    representation(['author'], IncludePolicy.allowAll(), ['articles': []]))

    then:
    result.included()*.id() == ['p1']
    result.sparseFieldsetLinkageExemptions() == [
      ResourceIdentity.ofId('people', 'p1')
    ] as Set
  }

  def "records no exemption when the fieldset keeps the relationship"() {
    given:
    backend.relationships['articles'] = [author: 'people']
    backend.relationshipValues('articles', 'author', 'p1')
    backend.identifiers['p1'] = ResourceIdentifier.of('people', 'p1')
    backend.rendered['p1'] = ResourceObject.of('people', 'p1')

    when:
    def result = engine.collectIncluded(['a1'], ['articles'], [primary('articles', '1')], null,
    representation(['author'], IncludePolicy.allowAll(), ['articles': ['author']]))

    then:
    result.included()*.id() == ['p1']
    result.sparseFieldsetLinkageExemptions().isEmpty()
  }

  def "reports conflicting included representations with the reaching include path"() {
    given:
    backend.relationships['articles'] = [author: 'people', reviewer: 'people']
    backend.relationshipValues('articles', 'author', 'p1')
    backend.relationshipValues('articles', 'reviewer', 'p1bis')
    backend.identifiers['p1'] = ResourceIdentifier.of('people', '1')
    backend.identifiers['p1bis'] = ResourceIdentifier.of('people', '1')
    backend.rendered['p1'] = new ResourceObject('people', '1', null,
        Attributes.ofAttributes([name: 'Ada']), null, null, null, Map.of())
    backend.rendered['p1bis'] = new ResourceObject('people', '1', null,
        Attributes.ofAttributes([name: 'Bea']), null, null, null, Map.of())

    when:
    engine.collectIncluded(['a1'], ['articles'], [primary('articles', '1')], null,
    representation(['author', 'reviewer']))

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.CONFLICTING_INCLUDED_REPRESENTATION
    exception.propertyPath() == null
    exception.message.contains("include path 'reviewer'")
  }

  def "enforces the included-resource count limit"() {
    given:
    backend.relationships['articles'] = [comments: 'comments']
    backend.relationshipValues('articles', 'comments', 'c1', 'c2')
    backend.identifiers['c1'] = ResourceIdentifier.of('comments', 'c1')
    backend.identifiers['c2'] = ResourceIdentifier.of('comments', 'c2')
    backend.rendered['c1'] = ResourceObject.of('comments', 'c1')
    backend.rendered['c2'] = ResourceObject.of('comments', 'c2')

    when:
    engine.collectIncluded(['a1'], ['articles'], [primary('articles', '1')], null,
    representation(['comments'], IncludePolicy.allowAll(), [:], 10, 1))

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.INCLUDE_COUNT_EXCEEDED
    exception.propertyPath() == null
    exception.message.contains("maxIncludedResources 1 via include path 'comments'")
  }

  private void nestedCommentBackend() {
    backend.relationships['articles'] = [comments: 'comments']
    backend.relationships['comments'] = [author: 'people']
    backend.relationshipValues('articles', 'comments', 'c1', 'c2')
    backend.relationshipValues('comments', 'author', 'p1', 'p2')
    backend.identifiers['c1'] = ResourceIdentifier.of('comments', 'c1')
    backend.identifiers['c2'] = ResourceIdentifier.of('comments', 'c2')
    backend.identifiers['p1'] = ResourceIdentifier.of('people', 'p1')
    backend.identifiers['p2'] = ResourceIdentifier.of('people', 'p2')
    backend.rendered['c1'] = ResourceObject.of('comments', 'c1')
    backend.rendered['c2'] = ResourceObject.of('comments', 'c2')
    backend.rendered['p1'] = ResourceObject.of('people', 'p1')
    backend.rendered['p2'] = ResourceObject.of('people', 'p2')
  }

  private static EffectiveRepresentation representation(
      List<String> includes,
      IncludePolicy includePolicy = IncludePolicy.allowAll(),
      Map<String, List<String>> fieldsets = [:],
      int maxDepth = 10,
      int maxIncluded = 100) {
    def builder = RepresentationSelection.builder()
    includes.each { String path -> builder.include(path) }
    fieldsets.each { String type, List<String> fields -> builder.fields(type, fields) }
    def policy = RepresentationPolicy.defaults()
        .withIncludePolicy(includePolicy)
        .withMaxIncludeDepth(maxDepth)
        .withMaxIncludedResources(maxIncluded)
    new EffectiveRepresentation(builder.build(), policy)
  }

  private static ResourceObject primary(String type, String id, String lid = null) {
    new ResourceObject(type, id, lid, null, null, null, null, Map.of())
  }
}
