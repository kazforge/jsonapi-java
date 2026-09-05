package io.github.kazemek.jsonapi.jackson3

import io.github.kazemek.jsonapi.core.model.JsonApiDocument
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic
import io.github.kazemek.jsonapi.jackson.mapping.RelationshipLinkage
import io.github.kazemek.jsonapi.jackson.representation.IncludePath
import io.github.kazemek.jsonapi.jackson.representation.IncludePolicy
import io.github.kazemek.jsonapi.jackson.representation.RelationshipAllowance
import io.github.kazemek.jsonapi.jackson.representation.RepresentationPolicy
import io.github.kazemek.jsonapi.jackson.representation.RepresentationSelection
import io.github.kazemek.jsonapi.fixtures.compoundwrite.AccessCountingArticle
import io.github.kazemek.jsonapi.fixtures.compoundwrite.BaseComment
import io.github.kazemek.jsonapi.fixtures.compoundwrite.ConflictArticle
import io.github.kazemek.jsonapi.fixtures.compoundwrite.CyclicNode
import io.github.kazemek.jsonapi.fixtures.compoundwrite.DeepNode
import io.github.kazemek.jsonapi.fixtures.compoundwrite.LinkedArticle
import io.github.kazemek.jsonapi.fixtures.compoundwrite.ModeratedComment
import io.github.kazemek.jsonapi.fixtures.compoundwrite.PolymorphicArticle
import io.github.kazemek.jsonapi.fixtures.compoundwrite.WrappedLinkageArticle
import io.github.kazemek.jsonapi.fixtures.domainpatch.AuthorIdMeta
import io.github.kazemek.jsonapi.fixtures.domainpatch.CommentIdMeta
import io.github.kazemek.jsonapi.fixtures.domainwrite.Article
import io.github.kazemek.jsonapi.fixtures.domainwrite.Comment
import io.github.kazemek.jsonapi.fixtures.domainwrite.Person
import io.github.kazemek.jsonapi.fixtures.domainwrite.Tag
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import tools.jackson.databind.json.JsonMapper

class CompoundSerializationSpec extends Specification {

  @Shared
  JsonApiResourceMapper mapper = JsonApiJackson3.resourceMapper(JsonMapper.builder().build())

  def "context-free mapping and an include policy alone omit included"() {
    expect:
    mapper.toDocument(article()).included() == null
    !mapper.toDocument(article()).hasIncludedMember()

    and:
    def document = mapper.toDocument(
        article(),
        null,
        RepresentationSelection.none(),
        includePolicy(IncludePolicy.allowAll()))
    document.included() == null
    !document.hasIncludedMember()
  }

  @Unroll
  def "writes compound document #id"() {
    given:
    def selection = selectionFor(includePaths)
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())

    when:
    JsonApiDocument document = mapper.toDocument(input, null, selection, policy)

    then:
    assertIncluded(document, expected)

    where:
    id | input | includePaths | expected
    "direct relationship" | new LinkedArticle("1", new LinkedArticle("2", null)) | ["related"] | [["articles", "2"]]
    "nested article relationships" | article() | ["comments.author"] | [
      ["comments", "5"],
      ["comments", "12"],
      ["people", "2"],
      ["people", "9"]
    ]
    "prefix-overlapping paths" | article() | ["comments", "comments.author"] | [
      ["comments", "5"],
      ["comments", "12"],
      ["people", "2"],
      ["people", "9"]
    ]
    "shared identity" | article() | ["comments.author", "author"] | [
      ["comments", "5"],
      ["comments", "12"],
      ["people", "2"],
      ["people", "9"]
    ]
    "self-reference to primary" | new LinkedArticle("1", new LinkedArticle("1", null)) | ["related"] | []
    "deep relationship path" | deepRoot() | ["child.child"] | [
      ["nodes", "2"],
      ["nodes", "3"]
    ]
    "cyclic relationship path" | cyclicRoot() | ["child.child.child"] | [["nodes", "2"]]
    "runtime polymorphic type" | polymorphicArticle() | ["comments"] | [["moderated-comments", "5"]]
    "wrapped relationship targets" | wrappedLinkageArticle() | ["author", "comments"] | [
      ["people", "p1"],
      ["comments", "c1"]
    ]
  }

  @Unroll
  def "rejects #description include request"() {
    when:
    mapper.toDocument(input, null, selectionFor(includePaths), policy)

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == diagnostic
    exception.propertyPath() == null
    exception.resourceClass() == resourceClass

    where:
    description | input | includePaths | policy | diagnostic | resourceClass
    "an unknown relationship" | article() | ["unknown"] | includePolicy(IncludePolicy.allowAll()) | MappingDiagnostic.INVALID_INCLUDE_PATH | Article.class
    "a denied relationship" | article() | ["author"] | includePolicy(IncludePolicy.denyAll()) | MappingDiagnostic.DENIED_RELATIONSHIP_INCLUDE | Article.class
    "a zero-depth path" | article() | ["author"] | includePolicy(IncludePolicy.allowAll(), 0, 100) | MappingDiagnostic.INCLUDE_DEPTH_EXCEEDED | Article.class
    "a path longer than its depth limit" | article() | ["comments.author"] | includePolicy(IncludePolicy.allowAll(), 1, 100) | MappingDiagnostic.INCLUDE_DEPTH_EXCEEDED | Article.class
    "the first included resource when the count is zero" | article() | ["author"] | includePolicy(IncludePolicy.allowAll(), 10, 0) | MappingDiagnostic.INCLUDE_COUNT_EXCEEDED | null
    "an included resource beyond the count limit" | article() | ["comments.author"] | includePolicy(IncludePolicy.allowAll(), 10, 2) | MappingDiagnostic.INCLUDE_COUNT_EXCEEDED | null
    "an unknown relationship before a policy denial" | article() | ["unknown"] | includePolicy(IncludePolicy.denyAll()) | MappingDiagnostic.INVALID_INCLUDE_PATH | Article.class
    "an unknown nested relationship before a later policy denial" | article() | ["comments.bogus"] | includePolicy(IncludePolicy.allowing(Set.of(RelationshipAllowance.of("articles", "comments")))) | MappingDiagnostic.INVALID_INCLUDE_PATH | Comment.class
    "a nested relationship denied for the runtime owner type" | polymorphicArticle() | ["comments.author"] | includePolicy(IncludePolicy.allowing(Set.of(RelationshipAllowance.of("articles", "comments"), RelationshipAllowance.of("comments", "author")))) | MappingDiagnostic.DENIED_RELATIONSHIP_INCLUDE | ModeratedComment.class
  }

  def "nested include policy matches the owner type at every segment"() {
    given:
    def selection = selectionFor(["comments.author"])
    def policy = includePolicy(IncludePolicy.allowing(Set.of(
        RelationshipAllowance.of("articles", "comments"),
        RelationshipAllowance.of("comments", "author"))))

    when:
    def document = mapper.toDocument(article(), null, selection, policy)

    then:
    assertIncluded(document, [
      ["comments", "5"],
      ["comments", "12"],
      ["people", "2"],
      ["people", "9"]
    ])
  }

  def "conflicting included representations fail before output is assembled"() {
    given:
    def input = new ConflictArticle(
        "10", new Person("1", "Alice"), new Person("1", "Bob"))
    def selection = selectionFor(["author", "reviewer"])

    when:
    mapper.toDocument(input, null, selection, includePolicy(IncludePolicy.allowAll()))

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.CONFLICTING_INCLUDED_REPRESENTATION
    exception.propertyPath() == null
    exception.resourceClass() == null
  }

  def "include traversal does not read an off-path relationship"() {
    given:
    def counting = new AccessCountingArticle("1", dan(), List.of(comment5()))
    def baseline = new AccessCountingArticle("1", dan(), List.of(comment5()))
    def selection = selectionFor(["author"])
    def policy = includePolicy(IncludePolicy.allowAll())

    when:
    def document = mapper.toDocument(counting, null, selection, policy)
    mapper.toDocument(baseline, null, RepresentationSelection.none(), policy)

    then:
    assertIncluded(document, [["people", "9"]])
    counting.authorReads == baseline.authorReads + 1
    counting.commentsReads == baseline.commentsReads
    counting.authorReads == 2
    counting.commentsReads == 1
  }

  def "heterogeneous primary collections validate every runtime resource type"() {
    given:
    def selection = selectionFor(["author"])

    when:
    mapper.toCollectionDocument(
        [article(), new Tag("java")], null, selection, includePolicy(IncludePolicy.allowAll()))

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.INVALID_INCLUDE_PATH
    exception.resourceClass() == Tag.class
  }

  def "one-shot primary iterables are materialized once for inclusion"() {
    given:
    def resources = onceIterable(
        new Article("1", "A", "B", List.of(), dan()),
        new Article("2", "C", "D", List.of(), ezra()))

    when:
    def document = mapper.toCollectionDocument(
        resources, null, selectionFor(["author"]), includePolicy(IncludePolicy.allowAll()))

    then:
    assertIncluded(document, [
      ["people", "9"],
      ["people", "2"]
    ])
  }

  def "an empty typed primary collection still validates include depth"() {
    given:
    def base = JsonMapper.builder().build()
    def selection = selectionFor(["author"])
    def policy = includePolicy(IncludePolicy.allowAll(), 0, 100)

    when:
    mapper.toCollectionDocument([], base.constructType(Article), null, selection, policy)

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.INCLUDE_DEPTH_EXCEEDED
    exception.resourceClass() == Article.class
  }

  def "multi-primary inclusion keeps first-discovery order across paths"() {
    given:
    def resources = [
      new Article("1", "A", "B", List.of(comment5()), dan()),
      new Article("2", "C", "D", List.of(comment12()), ezra())
    ]

    when:
    def document = mapper.toCollectionDocument(
        resources,
        null,
        selectionFor(["author", "comments"]),
        includePolicy(IncludePolicy.allowAll()))

    then:
    assertIncluded(document, [
      ["people", "9"],
      ["comments", "5"],
      ["people", "2"],
      ["comments", "12"]
    ])
  }

  def "concurrent compound mappings isolate included sets"() {
    given:
    def shared = JsonApiJackson3.resourceMapper(JsonMapper.builder().build())
    def start = new CountDownLatch(1)
    def done = new CountDownLatch(2)
    def failure = new AtomicReference<Throwable>()
    def pool = Executors.newFixedThreadPool(2)

    when:
    pool.submit({
      try {
        start.await()
        100.times {
          def document = shared.toDocument(
              new Article("1", "T", "B", List.of(), dan()),
              null,
              selectionFor(["author"]),
              includePolicy(IncludePolicy.allowAll()))
          assertIncluded(document, [["people", "9"]])
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
          def document = shared.toDocument(
              new Article("2", "T", "B", List.of(comment5()), null),
              null,
              selectionFor(["comments.author"]),
              includePolicy(IncludePolicy.allowAll()))
          assertIncluded(document, [
            ["comments", "5"],
            ["people", "2"]
          ])
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

    cleanup:
    pool.shutdownNow()
  }

  private static RepresentationSelection selectionFor(List<String> includePaths) {
    def builder = RepresentationSelection.builder()
    includePaths.each { path -> builder.include(IncludePath.of(path)) }
    return builder.build()
  }

  private static RepresentationPolicy includePolicy(
      IncludePolicy includePolicy, int maxDepth = 10, int maxIncluded = 100) {
    return RepresentationPolicy.defaults()
        .withIncludePolicy(includePolicy)
        .withMaxIncludeDepth(maxDepth)
        .withMaxIncludedResources(maxIncluded)
  }

  private static void assertIncluded(JsonApiDocument document, List<List<String>> expected) {
    assert document.hasIncludedMember()
    assert document.included() != null
    def actual = document.included().collect { resource ->
      [
        resource.type(),
        resource.id()
      ]
    }
    assert actual == expected
    assert actual.toSet().size() == actual.size()
  }

  private static Article article() {
    return new Article(
        "1",
        "Title",
        "Body",
        [
          new Comment("5", "First!", new Person("2", "Ezra")),
          new Comment("12", "I like XML better", new Person("9", "Dan"))
        ],
        new Person("9", "Dan"))
  }

  private static DeepNode deepRoot() {
    DeepNode leaf = new DeepNode("3", "leaf", null)
    DeepNode middle = new DeepNode("2", "middle", leaf)
    return new DeepNode("1", "root", middle)
  }

  private static CyclicNode cyclicRoot() {
    CyclicNode first = new CyclicNode("1", "first")
    CyclicNode second = new CyclicNode("2", "second")
    first.setChild(second)
    second.setChild(first)
    return first
  }

  private static PolymorphicArticle polymorphicArticle() {
    BaseComment comment = new ModeratedComment("5", "First!", new Person("2", "Ezra"))
    return new PolymorphicArticle("1", "Title", [comment])
  }

  private static WrappedLinkageArticle wrappedLinkageArticle() {
    return new WrappedLinkageArticle(
        "1",
        new RelationshipLinkage<>(new Person("p1", "Alice"), new AuthorIdMeta("editor")),
        [
          new RelationshipLinkage<>(new Comment("c1", "Hi", null), new CommentIdMeta(true))
        ])
  }

  private static Person dan() {
    return new Person("9", "Dan")
  }

  private static Person ezra() {
    return new Person("2", "Ezra")
  }

  private static Comment comment5() {
    return new Comment("5", "First!", ezra())
  }

  private static Comment comment12() {
    return new Comment("12", "I like XML better", dan())
  }

  private static Iterable<Object> onceIterable(Object... elements) {
    def values = List.of(elements)
    return new Iterable<Object>() {
          private boolean consumed

          @Override
          Iterator<Object> iterator() {
            if (consumed) {
              throw new IllegalStateException("iterable already consumed")
            }
            consumed = true
            return values.iterator()
          }
        }
  }
}
