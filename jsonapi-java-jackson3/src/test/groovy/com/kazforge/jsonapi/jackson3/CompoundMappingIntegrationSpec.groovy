package com.kazforge.jsonapi.jackson3

import com.kazforge.jsonapi.core.model.JsonApiDocument
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.representation.IncludePath
import com.kazforge.jsonapi.representation.IncludePolicy
import com.kazforge.jsonapi.representation.RelationshipAllowance
import com.kazforge.jsonapi.representation.RepresentationPolicy
import com.kazforge.jsonapi.representation.RepresentationSelection
import com.kazforge.jsonapi.fixtures.TestFixtureResources
import com.kazforge.jsonapi.fixtures.compoundwrite.AccessCountingArticle
import com.kazforge.jsonapi.fixtures.compoundwrite.LinkedArticle
import com.kazforge.jsonapi.fixtures.compoundwrite.ModeratedComment
import com.kazforge.jsonapi.fixtures.compoundwrite.PolymorphicArticle
import com.kazforge.jsonapi.fixtures.domainwrite.Article
import com.kazforge.jsonapi.fixtures.domainwrite.Comment
import com.kazforge.jsonapi.fixtures.domainwrite.Person
import com.kazforge.jsonapi.fixtures.domainwrite.Tag
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

class CompoundMappingIntegrationSpec extends Specification {

  def "include traversal does not read an off-path relationship"() {
    given:
    def mapper = JsonApiJackson3.resourceMapper(JsonMapper.builder().build())
    def counting = new AccessCountingArticle("1", dan(), List.of(comment()))
    def baseline = new AccessCountingArticle("1", dan(), List.of(comment()))
    def policy = includePolicy(IncludePolicy.allowAll())

    when:
    def document = mapper.toDocument(counting, null, selectionFor(["author"]), policy)
    mapper.toDocument(baseline, null, RepresentationSelection.none(), policy)

    then:
    assertIncluded(document, [["people", "9"]])
    counting.authorReads == baseline.authorReads + 1
    counting.commentsReads == baseline.commentsReads
    counting.authorReads == 2
    counting.commentsReads == 1
  }

  def "linked article inclusion matches the shared compound golden"() {
    given:
    def wireMapper = JsonMapper.builder().build()
    def writer = JsonApiJackson3.writer(wireMapper)
    def mapper = JsonApiJackson3.resourceMapper(wireMapper)

    when:
    def document = mapper.toDocument(
        new LinkedArticle("1", new LinkedArticle("2", null)), null,
        selectionFor(["related"]), includePolicy(IncludePolicy.allowAll()))

    then:
    wireMapper.readTree(writer.writeValueAsString(document)) ==
        wireMapper.readTree(TestFixtureResources.readCorpusUtf8("documents/compound-linked-article.json"))
  }

  def "heterogeneous primary collections validate every runtime resource type"() {
    when:
    JsonApiJackson3.resourceMapper(JsonMapper.builder().build()).toCollectionDocument(
        [article(), new Tag("java")], null, selectionFor(["author"]),
        includePolicy(IncludePolicy.allowAll()))

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.INVALID_INCLUDE_PATH
    exception.resourceClass() == Tag.class
  }

  def "one-shot primary iterables are materialized once for inclusion"() {
    given:
    def resources = onceIterable(
        new Article("1", "A", "B", List.of(), dan()),
        new Article("2", "C", "D", List.of(), new Person("2", "Ezra")))

    when:
    def document = JsonApiJackson3.resourceMapper(JsonMapper.builder().build()).toCollectionDocument(
        resources, null, selectionFor(["author"]), includePolicy(IncludePolicy.allowAll()))

    then:
    assertIncluded(document, [
      ["people", "9"],
      ["people", "2"]
    ])
  }

  def "nested include denial reports the runtime subtype as its owner"() {
    given:
    def article = new PolymorphicArticle("1", "Title", [
      new ModeratedComment("5", "First!", new Person("2", "Ezra"))
    ])
    def policy = includePolicy(IncludePolicy.allowing(Set.of(
        RelationshipAllowance.of("articles", "comments"),
        RelationshipAllowance.of("comments", "author"))))

    when:
    JsonApiJackson3.resourceMapper(JsonMapper.builder().build()).toDocument(
        article, null, selectionFor(["comments.author"]), policy)

    then:
    def exception = thrown(JsonApiMappingException)
    exception.diagnostic() == MappingDiagnostic.DENIED_RELATIONSHIP_INCLUDE
    exception.propertyPath() == null
    exception.resourceClass() == ModeratedComment.class
  }

  private static RepresentationSelection selectionFor(List<String> paths) {
    def builder = RepresentationSelection.builder()
    paths.each { path -> builder.include(IncludePath.of(path)) }
    builder.build()
  }

  private static RepresentationPolicy includePolicy(IncludePolicy policy) {
    RepresentationPolicy.defaults().withIncludePolicy(policy)
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
    new Article("1", "Title", "Body", List.of(comment()), dan())
  }

  private static Person dan() {
    new Person("9", "Dan")
  }

  private static Comment comment() {
    new Comment("5", "First!", new Person("2", "Ezra"))
  }

  private static Iterable<Object> onceIterable(Object... elements) {
    def values = List.of(elements)
    new Iterable<Object>() {
          private boolean consumed

          @Override
          Iterator<Object> iterator() {
            if (consumed) {
              throw new IllegalStateException("iterable already consumed")
            }
            consumed = true
            values.iterator()
          }
        }
  }
}
