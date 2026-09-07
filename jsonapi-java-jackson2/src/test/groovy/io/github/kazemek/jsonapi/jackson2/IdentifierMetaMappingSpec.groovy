package io.github.kazemek.jsonapi.jackson2

import io.github.kazemek.jsonapi.core.model.Meta
import io.github.kazemek.jsonapi.core.model.RelationshipData
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic
import io.github.kazemek.jsonapi.jackson.mapping.RelationshipLinkage
import io.github.kazemek.jsonapi.jackson2.IdentifierMetaFixtures.EncodedIdMeta
import io.github.kazemek.jsonapi.jackson2.IdentifierMetaFixtures.GenericIdentifierMetaArticle
import io.github.kazemek.jsonapi.jackson2.IdentifierMetaFixtures.IdMetaBox
import io.github.kazemek.jsonapi.jackson2.IdentifierMetaFixtures.NonEmittingIdentifierMetaArticle
import io.github.kazemek.jsonapi.jackson2.IdentifierMetaFixtures.ScalarIdMeta
import io.github.kazemek.jsonapi.jackson2.IdentifierMetaFixtures.ScalarSerializedMetaArticle
import io.github.kazemek.jsonapi.jackson2.IdentifierMetaFixtures.SerializedIdentifierMetaArticle
import io.github.kazemek.jsonapi.jackson2.IdentifierMetaFixtures.SilentIdMeta
import io.github.kazemek.jsonapi.jackson2.IdentifierMetaFixtures.SnakeIdMeta
import io.github.kazemek.jsonapi.jackson2.IdentifierMetaFixtures.SnakeIdentifierMeta
import spock.lang.Specification
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper

// Jackson 2 mechanism probes for identifier-meta: JavaType preservation, naming strategies, custom
// serializers (including non-emission and invalid scalar emission). Major-neutral RelationshipLinkage
// container, overlay, and inclusion semantics live in direct adapter-owned cases. Custom linkage
// mappers belong to the flat-read binder, which is a later Jackson 2 capability.
class IdentifierMetaMappingSpec extends Specification {

  static def mapper() {
    JsonApiJackson2.resourceMapper(JsonMapper.builder().build())
  }

  def "generic JavaType identifier meta is preserved on write"() {
    given:
    def article = new GenericIdentifierMetaArticle(
        "1",
        new RelationshipLinkage(ResourceIdentifier.of("people", "p1"), new IdMetaBox<Integer>(7)))

    when:
    def resource = mapper().toResource(article)

    then:
    def authorData = resource.relationships().relationships().get("author").data()
    authorData == new RelationshipData.SingleLinkage(
        new ResourceIdentifier("people", "p1", null, Meta.of([value: 7]), [:]))
  }

  def "configured Jackson naming applies to identifier meta members"() {
    given:
    def snakeMapper = JsonApiJackson2.resourceMapper(
        JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build())
    def article = new SnakeIdentifierMeta(
        "1", new RelationshipLinkage(ResourceIdentifier.of("people", "p1"), new SnakeIdMeta("editor")))

    when:
    def resource = snakeMapper.toResource(article)

    then:
    def authorData = resource.relationships().relationships().get("author").data()
    authorData == new RelationshipData.SingleLinkage(
        new ResourceIdentifier("people", "p1", null, Meta.of([display_role: "editor"]), [:]))
  }

  def "type-level serializer encodes identifier meta"() {
    given:
    def article = new SerializedIdentifierMetaArticle(
        "1", new RelationshipLinkage(ResourceIdentifier.of("people", "p1"), new EncodedIdMeta("editor")))

    when:
    def resource = mapper().toResource(article)

    then:
    def authorMembers = ((RelationshipData.SingleLinkage) resource.relationships()
        .relationships().get("author").data()).identifier().meta().members()
    authorMembers == [encoded: "editor"]
  }

  def "serializer non-emission leaves existing to-one identifier meta in place"() {
    given:
    def article = new NonEmittingIdentifierMetaArticle(
        "1",
        new RelationshipLinkage(
        new ResourceIdentifier("people", "p1", null, Meta.of([role: "editor"]), [:]),
        new SilentIdMeta("ignored")))

    when:
    def resource = mapper().toResource(article)

    then:
    def authorData = resource.relationships().relationships().get("author").data()
    authorData == new RelationshipData.SingleLinkage(
        new ResourceIdentifier("people", "p1", null, Meta.of([role: "editor"]), [:]))
  }

  def "converted scalar identifier meta is INVALID_META_TARGET"() {
    given:
    def article = new ScalarSerializedMetaArticle(
        "1", new RelationshipLinkage(ResourceIdentifier.of("people", "p1"), new ScalarIdMeta("editor")))

    when:
    mapper().toResource(article)

    then:
    def e = thrown(JsonApiMappingException)
    e.diagnostic == MappingDiagnostic.INVALID_META_TARGET
    e.propertyPath() == "/relationships/author/data/meta"
  }
}
