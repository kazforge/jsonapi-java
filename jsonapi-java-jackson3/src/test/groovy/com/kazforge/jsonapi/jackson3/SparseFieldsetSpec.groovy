package com.kazforge.jsonapi.jackson3

import com.kazforge.jsonapi.core.model.DocumentData
import com.kazforge.jsonapi.core.model.ResourceIdentity
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.mapping.MappedDocument
import com.kazforge.jsonapi.representation.FieldPolicy
import com.kazforge.jsonapi.representation.IncludePath
import com.kazforge.jsonapi.representation.IncludePolicy
import com.kazforge.jsonapi.representation.RepresentationPolicy
import com.kazforge.jsonapi.representation.RepresentationSelection
import com.kazforge.jsonapi.fixtures.domainwrite.Article
import com.kazforge.jsonapi.fixtures.domainwrite.Comment
import com.kazforge.jsonapi.fixtures.domainwrite.Person
import com.kazforge.jsonapi.fixtures.sparsefieldset.AccessCountingFieldsetArticle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import spock.lang.Specification
import spock.lang.Unroll
import tools.jackson.databind.json.JsonMapper

class SparseFieldsetSpec extends Specification {

  def "mapped collection applies a fieldset to each primary resource"() {
    given:
    def mapper = JsonApiJackson3.resourceMapper(JsonMapper.builder().build())
    def selection = selectionFor(["articles": ["title"]])

    when:
    def mapped = mapper.toMappedCollectionDocument(
        [
          article(),
          new Article("2", "Second", "Other", List.of(), null)
        ],
        null, selection, RepresentationPolicy.defaults())

    then:
    def resources = (mapped.document().data() as DocumentData.ResourceCollection).resources()
    resources*.id() == ["1", "2"]
    resources*.attributes()*.attributes() == [
      [title: "Title"],
      [title: "Second"]
    ]
    resources.every { it.relationships() == null }
  }

  @Unroll
  def "fieldset #description does not read excluded properties"() {
    given:
    def counting = new AccessCountingFieldsetArticle(
        "1", "Title", "Body", dan(), List.of(comment()))
    def selection = selectionFor(fieldsets, includePaths)
    def policy = includePaths.isEmpty()
        ? RepresentationPolicy.defaults()
        : RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())

    when:
    def mapped = JsonApiJackson3.resourceMapper(JsonMapper.builder().build())
        .toMappedDocument(counting, null, selection, policy)

    then:
    mapped != null
    counting.titleReads == titleReads
    counting.bodyReads == bodyReads
    counting.authorReads == authorReads
    counting.commentsReads == commentsReads

    where:
    description | fieldsets | includePaths | titleReads | bodyReads | authorReads | commentsReads
    "title-only without inclusion" | ["articles": ["title"]] | [] | 1 | 0 | 0 | 0
    "title-only with author inclusion" | ["articles": ["title"]] | ["author"] | 1 | 0 | 1 | 0
    "author-only without inclusion" | ["articles": ["author"]] | [] | 0 | 0 | 1 | 0
    "empty without inclusion" | ["articles": []] | [] | 0 | 0 | 0 | 0
  }

  def "field policy alone does not select fields"() {
    given:
    def policy = RepresentationPolicy.defaults().withFieldPolicy(FieldPolicy.denyAll())

    when:
    def mapped = JsonApiJackson3.resourceMapper(JsonMapper.builder().build())
        .toMappedDocument(article(), null, RepresentationSelection.none(), policy)

    then:
    mapped.document().data().resource().attributes().attributes() == [title: "Title", "body-text": "Body"]
    mapped.document().data().resource().relationships().relationships().keySet() == ["comments", "author"] as Set
    mapped.sparseFieldsetLinkageExemptions().isEmpty()
  }

  def "unmapped document rejects non-empty fieldsets"() {
    when:
    JsonApiJackson3.resourceMapper(JsonMapper.builder().build()).toDocument(
        article(), null, selectionFor(["articles": ["title"]]), RepresentationPolicy.defaults())

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.FIELDSETS_REQUIRE_MAPPED_DOCUMENT
    exception.propertyPath() == null
    exception.resourceClass() == null
    exception.message.contains("types: [articles]")
  }

  def "unmapped resource collection rejects non-empty fieldsets"() {
    when:
    JsonApiJackson3.resourceMapper(JsonMapper.builder().build()).toCollectionDocument(
        [article()], null, selectionFor(["articles": ["title"]]), RepresentationPolicy.defaults())

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.FIELDSETS_REQUIRE_MAPPED_DOCUMENT
    exception.propertyPath() == null
    exception.resourceClass() == null
  }

  def "concurrent fieldset mappings isolate documents and linkage exemptions"() {
    given:
    def shared = JsonApiJackson3.resourceMapper(JsonMapper.builder().build())
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
              article(), null, selectionFor(["articles": ["title"]], ["author"]),
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
              article(), null, selectionFor(["articles": ["title", "author", "comments"]], ["author"]),
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
    firstResult.get().document().data().resource().attributes().attributes() == [title: "Title"]
    secondResult.get().document().data().resource().attributes().attributes() == [title: "Title"]

    cleanup:
    pool.shutdownNow()
  }

  private static RepresentationSelection selectionFor(
      Map<String, List<String>> fieldsets, List<String> includePaths = []) {
    def builder = RepresentationSelection.builder()
    includePaths.each { path -> builder.include(IncludePath.of(path as String)) }
    fieldsets.each { type, fields -> builder.fields(type as String, fields as List<String>) }
    builder.build()
  }

  private static Article article() {
    new Article("1", "Title", "Body", List.of(comment()), dan())
  }

  private static Person dan() {
    new Person("9", "Dan")
  }

  private static Comment comment() {
    new Comment("5", "First!", new Person("2", "Ezra"))
  }
}
