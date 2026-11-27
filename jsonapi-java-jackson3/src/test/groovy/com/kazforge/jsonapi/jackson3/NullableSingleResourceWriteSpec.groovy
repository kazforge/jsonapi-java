package com.kazforge.jsonapi.jackson3

import com.kazforge.jsonapi.api.ResourceWriteOptions
import com.kazforge.jsonapi.core.model.JsonApiObject
import com.kazforge.jsonapi.core.model.Link
import com.kazforge.jsonapi.core.model.Links
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.document.DocumentEnvelope
import com.kazforge.jsonapi.fixtures.compoundwrite.BaseComment
import com.kazforge.jsonapi.fixtures.compoundwrite.ModeratedComment
import com.kazforge.jsonapi.fixtures.domainwrite.Article
import com.kazforge.jsonapi.fixtures.domainwrite.Comment
import com.kazforge.jsonapi.fixtures.generic.ParameterizedBindingFixtures.GenericValue
import com.kazforge.jsonapi.jackson3.CloseTrackingFixtures.TrackingOutputStream
import com.kazforge.jsonapi.mapping.ResourceDecoration
import com.kazforge.jsonapi.mapping.ResourceDecorator
import com.kazforge.jsonapi.mapping.ResourceDecoratorRegistry
import com.kazforge.jsonapi.representation.FieldPolicy
import com.kazforge.jsonapi.representation.IncludePath
import com.kazforge.jsonapi.representation.IncludePolicy
import com.kazforge.jsonapi.representation.RepresentationPolicy
import com.kazforge.jsonapi.representation.RepresentationSelection
import groovy.json.JsonSlurper
import spock.lang.Specification
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper

import java.lang.reflect.Type

class NullableSingleResourceWriteSpec extends Specification {

  def mapper = JsonMapper.builder().build()
  def runtime = JsonApiJackson3.builder(mapper).build()

  def "writes explicit null data when the resource is absent"() {
    when:
    def json = runtime.resources().writeNullableOne(null, Article)

    then:
    parse(json) == [data: null]
  }

  def "nullable writes honor envelope, configured jsonapi, and caller-owned streams"() {
    given:
    def configured = JsonApiJackson3.builder(mapper).jsonApiVersion("1.1").build()
    def options = ResourceWriteOptions.defaults().withEnvelope(
        DocumentEnvelope.ofLinks(Links.ofLinks([self: new Link.StringLink("/articles/1")]))
        .withMeta(Meta.of([request: "req-1"])))
    def out = new TrackingOutputStream(new ByteArrayOutputStream())
    def defaultOut = new TrackingOutputStream(new ByteArrayOutputStream())

    when:
    def json = configured.resources().writeNullableOne(null, Article, options)
    configured.resources().writeNullableOne(null, Article, options, out)
    configured.resources().writeNullableOne(null, Article, defaultOut)
    def override = configured.resources().writeNullableOne(
        null, Article, options.withEnvelope(
        options.envelope().withJsonApi(JsonApiObject.empty())))

    then:
    def document = parse(json)
    document.data == null
    document.links.self == "/articles/1"
    document.meta.request == "req-1"
    document.jsonapi.version == "1.1"
    parse(new String(out.bytes(), "UTF-8")) == document
    def defaultDocument = parse(new String(defaultOut.bytes(), "UTF-8"))
    defaultDocument.data == null
    !defaultDocument.containsKey("links")
    !defaultDocument.containsKey("meta")
    defaultDocument.jsonapi.version == "1.1"
    !out.closed
    !defaultOut.closed
    parse(override).jsonapi == [:]
  }

  def "present nullable writes keep ordinary typed mapping and fieldset provenance"() {
    given:
    def configured = JsonApiJackson3.builder(mapper)
        .representationPolicy(RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()))
        .build()
    def article = new Article("1", "T", "Body text", [
      new Comment("c1", "Nice", null)
    ], null)
    def options = ResourceWriteOptions.defaults().withSelection(
        RepresentationSelection.builder()
        .include(IncludePath.of("comments"))
        .fields("articles", "title")
        .build())
    def defaultOut = new TrackingOutputStream(new ByteArrayOutputStream())
    def optionsOut = new TrackingOutputStream(new ByteArrayOutputStream())

    when:
    def nullable = configured.resources().writeNullableOne(article, Article, options)
    def ordinary = configured.resources().writeOne(article, options)
    configured.resources().writeNullableOne(article, Article, defaultOut)
    configured.resources().writeNullableOne(article, Article, options, optionsOut)

    then:
    nullable == ordinary
    def document = parse(nullable)
    document.data.attributes == [title: "T"]
    !document.data.containsKey("relationships")
    document.included*.id == ["c1"]
    parse(new String(defaultOut.bytes(), "UTF-8")).data.id == "1"
    parse(new String(optionsOut.bytes(), "UTF-8")) == document
    !defaultOut.closed
    !optionsOut.closed
  }

  def "declared type governs present writes while runtime subtype decoration stays available"() {
    given:
    def links = Links.ofLinks([self: new Link.StringLink("/comments/c1")])
    ResourceDecorator<ModeratedComment> decorator = { comment -> ResourceDecoration.ofLinks(links) }
    def registry = ResourceDecoratorRegistry.builder().register(ModeratedComment, decorator).build()
    def configured = JsonApiJackson3.builder(mapper).decorators(registry).build()

    when:
    def present = configured.resources().writeNullableOne(
        new ModeratedComment("c1", "M", null), BaseComment)
    def absent = configured.resources().writeNullableOne(null, BaseComment)

    then:
    def document = parse(present)
    document.data.type == "comments"
    document.data.links.self == "/comments/c1"
    parse(absent).data == null
  }

  def "nullable writes require a complete declared resource type and an assignable value"() {
    when:
    runtime.resources().writeNullableOne(null, (Type) null)

    then:
    thrown(NullPointerException)

    when:
    runtime.resources().writeNullableOne(null, String)

    then:
    def missing = thrown(JsonApiMappingException)
    missing.diagnostic() == MappingDiagnostic.MISSING_RESOURCE_ANNOTATION

    when:
    runtime.resources().writeNullableOne(new Comment("c1", "B", null), Article)

    then:
    thrown(IllegalArgumentException)
  }

  def "nullable writes preserve declared generic types"() {
    given:
    def type = new TypeReference<GenericValue<String>>() {}.getType()

    when:
    def absent = runtime.resources().writeNullableOne(null, type)
    def present = runtime.resources().writeNullableOne(new GenericValue<String>("1", "v"), type)

    then:
    parse(absent).data == null
    parse(present).data.attributes.value == "v"

    when:
    runtime.resources().writeNullableOne(null, GenericValue)

    then:
    def unresolved = thrown(JsonApiMappingException)
    unresolved.diagnostic() == MappingDiagnostic.UNRESOLVED_GENERIC_TYPE
  }

  def "nullable writes validate include requests without a resource instance"() {
    given:
    def configured = JsonApiJackson3.builder(mapper).representationPolicy(policy).build()

    when:
    def json = configured.resources().writeNullableOne(
        null, Article, ResourceWriteOptions.defaults().withSelection(selection))

    then:
    parse(json).included == []

    where:
    selection | policy
    RepresentationSelection.builder().includeRequested().build() |
        RepresentationPolicy.defaults()
    RepresentationSelection.builder().include(IncludePath.of("comments")).build() |
        RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())
  }

  def "nullable writes reject invalid include requests without a resource instance"() {
    given:
    def configured = JsonApiJackson3.builder(mapper).representationPolicy(policy).build()

    when:
    configured.resources().writeNullableOne(
        null, Article, ResourceWriteOptions.defaults().withSelection(selection))

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == diagnostic

    where:
    selection | policy | diagnostic
    RepresentationSelection.builder().include(IncludePath.of("missing")).build() |
        RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()) |
        MappingDiagnostic.INVALID_INCLUDE_PATH
    RepresentationSelection.builder().include(IncludePath.of("comments")).build() |
        RepresentationPolicy.defaults() |
        MappingDiagnostic.DENIED_RELATIONSHIP_INCLUDE
    RepresentationSelection.builder().include(IncludePath.of("comments.author")).build() |
        RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()).withMaxIncludeDepth(1) |
        MappingDiagnostic.INCLUDE_DEPTH_EXCEEDED
  }

  def "nullable writes validate only the declared primary fieldset"() {
    given:
    def configured = JsonApiJackson3.builder(mapper).representationPolicy(policy).build()

    when:
    def json = configured.resources().writeNullableOne(
        null, Article, ResourceWriteOptions.defaults().withSelection(selection))

    then:
    parse(json).data == null

    where:
    selection | policy
    RepresentationSelection.builder().fields("articles", "title").build() |
        RepresentationPolicy.defaults()
    RepresentationSelection.builder().fields("comments", "bogus").build() |
        RepresentationPolicy.defaults()
  }

  def "nullable writes reject invalid declared primary fieldsets"() {
    given:
    def configured = JsonApiJackson3.builder(mapper).representationPolicy(policy).build()

    when:
    configured.resources().writeNullableOne(
        null, Article, ResourceWriteOptions.defaults().withSelection(selection))

    then:
    def failure = thrown(JsonApiMappingException)
    failure.diagnostic() == diagnostic

    where:
    selection | policy | diagnostic
    RepresentationSelection.builder().fields("articles", "bogus").build() |
        RepresentationPolicy.defaults() |
        MappingDiagnostic.INVALID_FIELDSET_FIELD
    RepresentationSelection.builder().fields("articles", "title").build() |
        RepresentationPolicy.defaults().withFieldPolicy(FieldPolicy.denyAll()) |
        MappingDiagnostic.DENIED_FIELDSET_FIELD
  }

  def "nullable writes do not materialize or decorate an absent resource"() {
    given:
    ResourceDecorator<Article> decorator = { article -> throw new AssertionError("decorator invoked") }
    def registry = ResourceDecoratorRegistry.builder().register(Article, decorator).build()
    def configured = JsonApiJackson3.builder(mapper).decorators(registry).build()

    expect:
    parse(configured.resources().writeNullableOne(null, Article)).data == null
  }

  private static Map<String, Object> parse(String json) {
    new JsonSlurper().parseText(json) as Map<String, Object>
  }
}
