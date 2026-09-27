package com.kazforge.jsonapi.jackson2

import com.fasterxml.jackson.databind.json.JsonMapper
import com.kazforge.jsonapi.core.model.DocumentData
import com.kazforge.jsonapi.core.model.ResourceIdentity
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.fixtures.domainwrite.Article
import com.kazforge.jsonapi.fixtures.domainwrite.Comment
import com.kazforge.jsonapi.fixtures.domainwrite.Person
import com.kazforge.jsonapi.fixtures.sparsefieldset.AccessCountingFieldsetArticle
import com.kazforge.jsonapi.mapping.MappedDocument
import com.kazforge.jsonapi.representation.FieldPolicy
import com.kazforge.jsonapi.representation.IncludePath
import com.kazforge.jsonapi.representation.IncludePolicy
import com.kazforge.jsonapi.representation.RepresentationPolicy
import com.kazforge.jsonapi.representation.RepresentationSelection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class SparseFieldsetSpec extends Specification {

  @Shared
  JsonApiResourceMapper mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())

  def "mapped collection applies a sparse fieldset through the adapter route"() {
    given:
    def selection = selectionFor([articles: ["title"]])

    when:
    def mapped = mapper.toMappedCollectionDocument(
        [article()], null, selection, RepresentationPolicy.defaults())

    then:
    def data = mapped.document().data() as DocumentData.ResourceCollection
    data.resources()*.attributes()*.attributes() == [[title: "Title"]]
    data.resources()*.relationships() == [null]
    mapped.sparseFieldsetLinkageExemptions().isEmpty()
  }

  @Unroll
  def "fieldset #description does not read excluded properties"() {
    given:
    def counting = new AccessCountingFieldsetArticle(
        "1", "Title", "Body", dan(), List.of(comment5()))
    def selection = selectionFor(fieldsets, includePaths)
    def policy = includePaths.isEmpty()
        ? RepresentationPolicy.defaults()
        : RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())

    when:
    def mapped = mapper.toMappedDocument(counting, null, selection, policy)

    then:
    mapped != null
    counting.titleReads == titleReads
    counting.bodyReads == bodyReads
    counting.authorReads == authorReads
    counting.commentsReads == commentsReads

    where:
    description | fieldsets | includePaths | titleReads | bodyReads | authorReads | commentsReads
    "title-only without inclusion" | [articles: ["title"]] | [] | 1 | 0 | 0 | 0
    "title-only with author inclusion" | [articles: ["title"]] | ["author"] | 1 | 0 | 1 | 0
    "author-only without inclusion" | [articles: ["author"]] | [] | 0 | 0 | 1 | 0
    "empty without inclusion" | [articles: []] | [] | 0 | 0 | 0 | 0
  }

  def "field policy alone does not select fields"() {
    given:
    def policy = RepresentationPolicy.defaults().withFieldPolicy(FieldPolicy.denyAll())

    when:
    def mapped = mapper.toMappedDocument(
        article(), null, RepresentationSelection.none(), policy)

    then:
    def primary = (mapped.document().data() as DocumentData.SingleResource).resource()
    primary.attributes().attributes() == [title: "Title", "body-text": "Body"]
    primary.relationships().relationships().keySet() == ["comments", "author"] as Set
    mapped.sparseFieldsetLinkageExemptions().isEmpty()
  }

  def "unmapped document rejects non-empty fieldsets"() {
    when:
    mapper.toDocument(article(), null, selectionFor([articles: ["title"]]), RepresentationPolicy.defaults())

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.FIELDSETS_REQUIRE_MAPPED_DOCUMENT
    exception.propertyPath() == null
    exception.resourceClass() == null
    exception.message.contains("types: [articles]")
  }

  def "unmapped resource collection rejects non-empty fieldsets"() {
    when:
    mapper.toCollectionDocument(
        [article()], null, selectionFor([articles: ["title"]]), RepresentationPolicy.defaults())

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.FIELDSETS_REQUIRE_MAPPED_DOCUMENT
    exception.propertyPath() == null
    exception.resourceClass() == null
  }

  def "concurrent fieldset mappings isolate documents and linkage exemptions"() {
    given:
    def shared = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())
    def start = new CountDownLatch(1)
    def done = new CountDownLatch(2)
    def firstResult = new AtomicReference<MappedDocument>()
    def secondResult = new AtomicReference<MappedDocument>()
    def failure = new AtomicReference<Throwable>()
    def pool = Executors.newFixedThreadPool(2)

    when:
    pool.submit({
      try {
        start.await()
        100.times {
          firstResult.set(shared.toMappedDocument(
              article(), null, selectionFor([articles: ["title"]], ["author"]),
              RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())))
        }
      } catch (Throwable throwable) {
        failure.compareAndSet(null, throwable)
      } finally {
        done.countDown()
      }
    } as Runnable)
    pool.submit({
      try {
        start.await()
        100.times {
          secondResult.set(shared.toMappedDocument(
              article(), null, selectionFor([articles: ["title", "author", "comments"]], ["author"]),
              RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())))
        }
      } catch (Throwable throwable) {
        failure.compareAndSet(null, throwable)
      } finally {
        done.countDown()
      }
    } as Runnable)
    start.countDown()

    then:
    done.await(10, TimeUnit.SECONDS)
    failure.get() == null
    firstResult.get().sparseFieldsetLinkageExemptions() == Set.of(ResourceIdentity.ofId("people", "9"))
    secondResult.get().sparseFieldsetLinkageExemptions().isEmpty()
    def first = (firstResult.get().document().data() as DocumentData.SingleResource).resource()
    def second = (secondResult.get().document().data() as DocumentData.SingleResource).resource()
    first.attributes().attributes() == [title: "Title"]
    second.attributes().attributes() == [title: "Title"]

    cleanup:
    pool.shutdownNow()
  }

  private static RepresentationSelection selectionFor(
      Map<String, List<String>> fieldsets, List<String> includePaths = []) {
    def builder = RepresentationSelection.builder()
    includePaths.each { path -> builder.include(IncludePath.of(path)) }
    fieldsets.each { type, fields -> builder.fields(type, fields) }
    builder.build()
  }

  private static Article article() {
    new Article("1", "Title", "Body", List.of(comment5()), dan())
  }

  private static Person dan() {
    new Person("9", "Dan")
  }

  private static Comment comment5() {
    new Comment("5", "First!", new Person("2", "Ezra"))
  }
}
