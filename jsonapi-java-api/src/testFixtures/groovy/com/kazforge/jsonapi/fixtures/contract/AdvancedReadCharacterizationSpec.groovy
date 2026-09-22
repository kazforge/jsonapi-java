package com.kazforge.jsonapi.fixtures.contract

import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.fixtures.domainpatch.AuthorIdMeta
import com.kazforge.jsonapi.fixtures.domainpatch.CommentIdMeta
import com.kazforge.jsonapi.fixtures.domainread.FlatArticle
import com.kazforge.jsonapi.fixtures.domainread.FlatArticleWithArray
import com.kazforge.jsonapi.fixtures.domainread.FlatArticleWithOptional
import com.kazforge.jsonapi.fixtures.domainread.FlatArticleWithSet
import com.kazforge.jsonapi.fixtures.domainread.FlatMappedTargetArticle
import com.kazforge.jsonapi.fixtures.domainread.FlatRelationshipLinkageArticle
import com.kazforge.jsonapi.fixtures.domainread.FlatWrappedMappedTargetArticle
import com.kazforge.jsonapi.fixtures.domainread.MappedReadTarget
import com.kazforge.jsonapi.mapping.RelationshipLinkage
import spock.lang.Specification

/**
 * Advanced relationship-linkage read characterization contract observed through the Level-1 {@code
 * readOne} entry point: direct to-one/to-many {@code ResourceIdentifier} binding preserves
 * identifier meta and drops additional identifier members, explicit-null and present-empty linkage
 * states bind as their own values, {@code Optional}/array/{@code Set} containers bind in container
 * order, opt-in {@code RelationshipLinkage} occurrences pair each target with its own declared
 * identifier meta, and configured custom linkage mappers bind ordinary and wrapped targets.
 * Concrete adapter subclasses supply the configured runtimes, including a registry with a
 * {@code MappedReadTarget} mapper.
 */
abstract class AdvancedReadCharacterizationSpec extends Specification {

  protected abstract JsonApi api()

  /**
   * Returns a runtime whose configured linkage-mapper registry maps {@code MappedReadTarget}. When
   * {@code nullMapper} is true the registered mapper returns {@code null} for every invocation.
   */
  protected abstract JsonApi mappedApi(boolean nullMapper)

  def "binds a direct to-one identifier preserving meta and dropping additional members"() {
    given:
    def json =
        '{"data":{"type":"articles","id":"1","relationships":{"author":{"data":' +
        '{"type":"people","id":"p1","meta":{"role":"editor"},"@extra":"dropped"}}}}}'

    when:
    def bound = api().resources().readOne(json, FlatArticle)

    then:
    bound.author == new ResourceIdentifier("people", "p1", null, Meta.of([role: "editor"]), [:])
  }

  def "binds a direct to-many identifier collection preserving per-element meta"() {
    given:
    def json =
        '{"data":{"type":"articles","id":"1","relationships":{"comments":{"data":[' +
        '{"type":"comments","id":"c1","meta":{"pinned":true}},{"type":"comments","id":"c2"}]}}}}'

    when:
    def bound = api().resources().readOne(json, FlatArticle)

    then:
    bound.comments == [
      new ResourceIdentifier("comments", "c1", null, Meta.of([pinned: true]), [:]),
      ResourceIdentifier.of("comments", "c2")
    ]
  }

  def "binds explicit-null to-one and present-empty to-many linkage states"() {
    given:
    def json =
        '{"data":{"type":"articles","id":"1","relationships":{' +
        '"author":{"data":null},"comments":{"data":[]}}}}'

    when:
    def bound = api().resources().readOne(json, FlatArticle)

    then:
    bound.author == null
    bound.comments == []
  }

  def "binds present and explicit-null Optional to-one relationships"() {
    when:
    def present = api().resources().readOne(
        '{"data":{"type":"articles","id":"1","relationships":{"author":{"data":{"type":"people","id":"p1"}}}}}',
        FlatArticleWithOptional)

    then:
    present.author == Optional.of(ResourceIdentifier.of("people", "p1"))

    when:
    def absent = api().resources().readOne(
        '{"data":{"type":"articles","id":"1","relationships":{"author":{"data":null}}}}',
        FlatArticleWithOptional)

    then:
    absent.author == Optional.empty()
  }

  def "binds an array to-many container in linkage order"() {
    given:
    def json =
        '{"data":{"type":"articles","id":"1","relationships":{"comments":{"data":[' +
        '{"type":"comments","id":"c1"},{"type":"comments","id":"c2"}]}}}}'

    when:
    def bound = api().resources().readOne(json, FlatArticleWithArray)

    then:
    bound.comments*.type() == ["comments", "comments"]
    bound.comments*.id() == ["c1", "c2"]
  }

  def "binds a set to-many container without depending on set order"() {
    given:
    def json =
        '{"data":{"type":"articles","id":"1","relationships":{"tags":{"data":[' +
        '{"type":"tags","id":"t1"},{"type":"tags","id":"t2"}]}}}}'

    when:
    def bound = api().resources().readOne(json, FlatArticleWithSet)

    then:
    bound.tags == [
      ResourceIdentifier.of("tags", "t1"),
      ResourceIdentifier.of("tags", "t2")
    ] as Set
  }

  def "binds a wrapped to-one occurrence with its declared identifier meta"() {
    given:
    def json =
        '{"data":{"type":"articles","id":"1","relationships":{"author":{"data":' +
        '{"type":"people","id":"p1","meta":{"role":"editor"}}}}}}'

    when:
    def bound = api().resources().readOne(json, FlatRelationshipLinkageArticle)

    then:
    bound.author == new RelationshipLinkage<>(
        new ResourceIdentifier("people", "p1", null, Meta.of([role: "editor"]), [:]),
        new AuthorIdMeta("editor"))
    bound.comments == null
  }

  def "binds wrapped to-many occurrences pairing each target with its own meta"() {
    given:
    def json =
        '{"data":{"type":"articles","id":"1","relationships":{"comments":{"data":[' +
        '{"type":"comments","id":"c1","meta":{"pinned":true}},{"type":"comments","id":"c2"}]}}}}'

    when:
    def bound = api().resources().readOne(json, FlatRelationshipLinkageArticle)

    then:
    bound.comments == [
      new RelationshipLinkage<>(
      new ResourceIdentifier("comments", "c1", null, Meta.of([pinned: true]), [:]),
      new CommentIdMeta(true)),
      new RelationshipLinkage<>(ResourceIdentifier.of("comments", "c2"), null)
    ]
    bound.author == null
  }

  def "binds ordinary custom-mapper to-one and to-many relationships"() {
    given:
    def json =
        '{"data":{"type":"articles","id":"1","relationships":{' +
        '"author":{"data":{"type":"people","id":"p1"}},' +
        '"contributors":{"data":[{"type":"people","id":"p1"},{"type":"people","id":"p2"}]}}}}'

    when:
    def bound = mappedApi(false).resources().readOne(json, FlatMappedTargetArticle)

    then:
    bound.author == new MappedReadTarget("people", "p1")
    bound.contributors == [
      new MappedReadTarget("people", "p1"),
      new MappedReadTarget("people", "p2")
    ]
  }

  def "binds wrapped custom-mapper to-many occurrences pairing target and meta"() {
    given:
    def json =
        '{"data":{"type":"articles","id":"1","relationships":{"comments":{"data":[' +
        '{"type":"comments","id":"c1","meta":{"pinned":true}},{"type":"comments","id":"c2"}]}}}}'

    when:
    def bound = mappedApi(false).resources().readOne(json, FlatWrappedMappedTargetArticle)

    then:
    bound.comments == [
      new RelationshipLinkage<>(new MappedReadTarget("comments", "c1"), new CommentIdMeta(true)),
      new RelationshipLinkage<>(new MappedReadTarget("comments", "c2"), null)
    ]
  }

  def "binds null custom-mapper results as null ordinary and wrapped to-one values"() {
    given:
    def author =
        '{"data":{"type":"articles","id":"1","relationships":{"author":{"data":' +
        '{"type":"people","id":"p1","meta":{"role":"editor"}}}}}}'

    when:
    def ordinary = mappedApi(true).resources().readOne(author, FlatMappedTargetArticle)
    def wrapped = mappedApi(true).resources().readOne(author, FlatWrappedMappedTargetArticle)

    then:
    ordinary.author == null
    wrapped.author == null
  }

  def "fails a wrapped to-many occurrence with a null mapper result at its indexed location"() {
    given:
    def json =
        '{"data":{"type":"articles","id":"1","relationships":{"comments":{"data":[' +
        '{"type":"comments","id":"c1","meta":{"pinned":true}},{"type":"comments","id":"c2"}]}}}}'

    when:
    mappedApi(true).resources().readOne(json, FlatWrappedMappedTargetArticle)

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == MappingDiagnostic.LINKAGE_MAPPING_FAILED
    failure.propertyPath() == "/relationships/comments/data/0"
  }
}
