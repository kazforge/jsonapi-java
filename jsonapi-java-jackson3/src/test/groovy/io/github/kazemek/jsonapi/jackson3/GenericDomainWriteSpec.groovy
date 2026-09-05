package io.github.kazemek.jsonapi.jackson3

import io.github.kazemek.jsonapi.core.model.DocumentData
import io.github.kazemek.jsonapi.core.model.RelationshipData
import io.github.kazemek.jsonapi.jackson.representation.IncludePath
import io.github.kazemek.jsonapi.jackson.representation.IncludePolicy
import io.github.kazemek.jsonapi.jackson.representation.RepresentationPolicy
import io.github.kazemek.jsonapi.jackson.representation.RepresentationSelection
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic
import io.github.kazemek.jsonapi.fixtures.compoundwrite.ModeratedComment
import io.github.kazemek.jsonapi.fixtures.compoundwrite.PolymorphicArticle
import io.github.kazemek.jsonapi.jackson3.GenericDomainWriteFixtures.GenericResource
import io.github.kazemek.jsonapi.jackson3.GenericDomainWriteFixtures.GenericRelationship
import io.github.kazemek.jsonapi.jackson3.GenericDomainWriteFixtures.GenericThing
import io.github.kazemek.jsonapi.jackson3.GenericDomainWriteFixtures.IrrelevantGeneric
import io.github.kazemek.jsonapi.jackson3.GenericDomainWriteFixtures.RawMapGeneric
import io.github.kazemek.jsonapi.jackson3.GenericDomainWriteFixtures.RawCollectionRelationship
import io.github.kazemek.jsonapi.jackson3.GenericDomainWriteFixtures.RawRelationship
import io.github.kazemek.jsonapi.jackson3.GenericDomainWriteFixtures.OtherThing
import io.github.kazemek.jsonapi.jackson3.GenericDomainWriteFixtures.ScalarValue
import io.github.kazemek.jsonapi.jackson3.GenericDomainWriteFixtures.ScalarView
import io.github.kazemek.jsonapi.jackson3.GenericDomainWriteFixtures.ThingResource
import io.github.kazemek.jsonapi.jackson3.GenericDomainWriteFixtures.WildcardRelationship
import io.github.kazemek.jsonapi.fixtures.compoundwrite.BaseComment
import spock.lang.Specification
import tools.jackson.databind.JavaType
import tools.jackson.databind.json.JsonMapper

class GenericDomainWriteSpec extends Specification {

  def "ordinary concrete resource writes remain convenient"() {
    given:
    def mapper = mapper()

    when:
    def resource = mapper.toResource(new GenericThing("t1", "Thing"))

    then:
    resource.type() == "things"
    resource.id() == "t1"
    resource.attributes().attributes() == [name: "Thing"]
  }

  def "directly parameterized roots use the supplied JavaType"() {
    given:
    def base = JsonMapper.builder().build()
    def mapper = JsonApiJackson3.resourceMapper(base)
    def rootType = parameterized(base, GenericResource, GenericThing)
    def related = new GenericThing("t1", "Thing")
    def root = new GenericResource<GenericThing>("r1", related, related, [related], Optional.of(related))

    when:
    def resource = mapper.toResource(root, rootType)
    def document = mapper.toDocument(root, rootType, null)

    then:
    resource.type() == "generic-resources"
    resource.relationships().relationships().related.data().identifier().type() == "things"
    ((DocumentData.SingleResource) document.data()).resource().id() == "r1"
  }

  def "typed collection routes retain the declared root through inclusion"() {
    given:
    def base = JsonMapper.builder().build()
    def mapper = JsonApiJackson3.resourceMapper(base)
    def rootType = parameterized(base, GenericResource, GenericThing)
    def related = new GenericThing("t1", "Thing")
    def root = new GenericResource<GenericThing>("r1", null, related, [], Optional.empty())
    def selection = RepresentationSelection.builder().include(IncludePath.of("related")).build()
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())

    when:
    def collection = mapper.toCollectionDocument([root], rootType, null)
    def compound = mapper.toCollectionDocument([root], rootType, null, selection, policy)
    def mapped = mapper.toMappedCollectionDocument([root], rootType, null, selection, policy)

    then:
    ((DocumentData.ResourceCollection) collection.data()).resources()[0].id() == "r1"
    compound.included()*.type() == ["things"]
    mapped.document().included()*.type() == ["things"]
  }

  def "typed primary collections specialize runtime subtypes"() {
    given:
    def base = JsonMapper.builder().build()
    def mapper = JsonApiJackson3.resourceMapper(base)
    def resourceType = base.constructType(BaseComment)
    def resources = [
      new BaseComment("c1", "Base", null),
      new ModeratedComment("c1", "Moderated", null)
    ]

    when:
    def document = mapper.toCollectionDocument(resources, resourceType, null)

    then:
    ((DocumentData.ResourceCollection) document.data()).resources()*.type() ==
        [
          "comments",
          "moderated-comments"
        ]
  }

  def "empty typed collections validate unknown include paths"() {
    given:
    def base = JsonMapper.builder().build()
    def mapper = JsonApiJackson3.resourceMapper(base)
    def rootType = parameterized(base, GenericResource, GenericThing)
    def selection = RepresentationSelection.builder().include(IncludePath.of("missing")).build()
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())

    when:
    mapper.toCollectionDocument([], rootType, null, selection, policy)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.INVALID_INCLUDE_PATH
  }

  def "empty typed collections validate denied include paths"() {
    given:
    def base = JsonMapper.builder().build()
    def mapper = JsonApiJackson3.resourceMapper(base)
    def rootType = parameterized(base, GenericResource, GenericThing)
    def selection = RepresentationSelection.builder().include(IncludePath.of("related")).build()
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.denyAll())

    when:
    mapper.toCollectionDocument([], rootType, null, selection, policy)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.DENIED_RELATIONSHIP_INCLUDE
  }

  def "polymorphic relationship linkage matches the specialized included resource"() {
    given:
    def selection = RepresentationSelection.builder().include(IncludePath.of("comments")).build()
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())
    def article = new PolymorphicArticle(
        "a1", "Article", [
          new ModeratedComment("c1", "Comment", null)
        ])

    when:
    def document = mapper().toDocument(article, null, selection, policy)

    then:
    document.data().resource().relationships().relationships().comments.data().identifiers()
        .get(0).type() == "moderated-comments"
    document.included()*.type() == ["moderated-comments"]
  }

  def "a raw runtime-class route cannot silently write an unresolved generic member"() {
    given:
    def mapper = mapper()
    def related = new GenericThing("t1", "Thing")
    def raw = new GenericResource("r1", related, related, [related], Optional.of(related))

    when:
    mapper.toResource(raw)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNRESOLVED_GENERIC_TYPE
    ex.propertyPath() == "/attributes/value"
  }

  def "generic scalar properties use their bound type for configured serialization"() {
    given:
    def base = JsonMapper.builder().build()
    def mapper = JsonApiJackson3.resourceMapper(base)
    def rootType = parameterized(base, GenericResource, ScalarView)
    def root = new GenericResource<ScalarView>(
        "r1", new ScalarValue("base", "extra"), null, [], Optional.empty())

    when:
    def resource = mapper.toResource(root, rootType)

    then:
    resource.attributes().attributes().value == [base: "base"]
  }

  def "generic list relationships resolve their declared element type"() {
    given:
    def base = JsonMapper.builder().build()
    def mapper = JsonApiJackson3.resourceMapper(base)
    def rootType = parameterized(base, GenericResource, GenericThing)
    def thing = new GenericThing("t1", "Thing")
    def root = new GenericResource<GenericThing>("r1", null, null, [thing], Optional.empty())

    when:
    def relationship = mapper.toResource(root, rootType).relationships().relationships().many

    then:
    relationship.data() instanceof RelationshipData.IdentifierCollectionLinkage
    relationship.data().identifiers().get(0).type() == "things"
    relationship.data().identifiers().get(0).id() == "t1"
  }

  def "generic optional relationships resolve populated and empty values consistently"() {
    given:
    def base = JsonMapper.builder().build()
    def mapper = JsonApiJackson3.resourceMapper(base)
    def rootType = parameterized(base, GenericResource, GenericThing)
    def thing = new GenericThing("t1", "Thing")

    when:
    def populated = mapper.toResource(
        new GenericResource<GenericThing>("r1", null, null, [], Optional.of(thing)), rootType)
    def empty = mapper.toResource(
        new GenericResource<GenericThing>("r2", null, null, [], Optional.empty()), rootType)

    then:
    populated.relationships().relationships().optional.data().identifier().type() == "things"
    populated.relationships().relationships().optional.data().identifier().id() == "t1"
    empty.relationships().relationships().optional.data() == RelationshipData.NullLinkage.INSTANCE
  }

  def "empty generic collections and optionals do not require runtime element inference"() {
    given:
    def base = JsonMapper.builder().build()
    def mapper = JsonApiJackson3.resourceMapper(base)
    def rootType = parameterized(base, GenericResource, GenericThing)
    def root = new GenericResource<GenericThing>("r1", null, null, [], Optional.empty())
    def selection = RepresentationSelection.builder()
        .include(IncludePath.of("many"))
        .include(IncludePath.of("optional"))
        .build()
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())

    when:
    def document = mapper.toDocument(root, rootType, null, selection, policy)

    then:
    document.included() != null
    document.included().isEmpty()
    ((DocumentData.SingleResource) document.data()).resource()
        .relationships().relationships().many.data().identifiers().isEmpty()
    ((DocumentData.SingleResource) document.data()).resource()
        .relationships().relationships().optional.data() == RelationshipData.NullLinkage.INSTANCE
  }

  def "compound inclusion through generic direct, collection, and optional relationships preserves type"() {
    given:
    def base = JsonMapper.builder().build()
    def mapper = JsonApiJackson3.resourceMapper(base)
    def rootType = parameterized(base, GenericResource, GenericThing)
    def thing = new GenericThing("t1", "Thing")
    def root = new GenericResource<GenericThing>("r1", thing, thing, [thing], Optional.of(thing))
    def selection = RepresentationSelection.builder()
        .include(IncludePath.of("related"))
        .include(IncludePath.of("many"))
        .include(IncludePath.of("optional"))
        .build()
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())

    when:
    def document = mapper.toDocument(root, rootType, null, selection, policy)

    then:
    document.included()*.type() == ["things"]
    document.included()[0].id() == "t1"
  }

  def "concrete generic subclasses use their bound superclass through the convenience route"() {
    given:
    def mapper = mapper()
    def thing = new GenericThing("t1", "Thing")
    def resource = new ThingResource("r1", thing, thing, [thing], Optional.of(thing))

    when:
    def mapped = mapper.toResource(resource)

    then:
    mapped.type() == "generic-resources"
    mapped.relationships().relationships().related.data().identifier().type() == "things"
    mapped.relationships().relationships().many.data().identifiers().get(0).type() == "things"
  }

  def "unresolved variables on ignored members do not invalidate an otherwise valid write"() {
    expect:
    mapper().toResource(new IrrelevantGeneric("r1", new Object())).id() == "r1"
  }

  def "wildcard generic containers that do not reference the root variable remain writable"() {
    expect:
    mapper().toResource(new RawMapGeneric("r1", ["key": "value"], new Object()))
    .attributes().attributes() == [values: [key: "value"]]
  }

  def "parameterized mapping cache entries stay distinct for the same raw root"() {
    given:
    def base = JsonMapper.builder().build()
    def mapper = JsonApiJackson3.resourceMapper(base)
    def thingType = parameterized(base, GenericResource, GenericThing)
    def otherType = parameterized(base, GenericResource, OtherThing)
    def thing = new GenericThing("t1", "Thing")
    def other = new OtherThing("o1", "Other")

    when:
    def first = mapper.toResource(
        new GenericResource<GenericThing>("r1", null, thing, [], Optional.empty()), thingType)
    def second = mapper.toResource(
        new GenericResource<OtherThing>("r2", null, other, [], Optional.empty()), otherType)

    then:
    first.relationships().relationships().related.data().identifier().type() == "things"
    second.relationships().relationships().related.data().identifier().type() == "other-things"
  }

  def "generic relationship failures report the JSON:API member location"() {
    given:
    def mapper = mapper()
    def thing = new GenericThing("t1", "Thing")
    def raw = new GenericRelationship("r1", thing)

    when:
    mapper.toResource(raw)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNRESOLVED_GENERIC_TYPE
    ex.propertyPath() == "/relationships/related/data"
  }

  def "raw optional relationships fail before runtime target inference"() {
    when:
    mapper().toResource(new RawRelationship("r1", Optional.of(new GenericThing("t1", "Thing"))))

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNRESOLVED_GENERIC_TYPE
    ex.propertyPath() == "/relationships/relation/data"
  }

  def "wildcard optional relationships fail even when empty"() {
    when:
    mapper().toResource(new WildcardRelationship("r1", Optional.empty()))

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNRESOLVED_GENERIC_TYPE
    ex.propertyPath() == "/relationships/relation/data"
  }

  def "raw collection relationships fail before empty linkage is emitted"() {
    when:
    mapper().toResource(new RawCollectionRelationship("r1", []))

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNRESOLVED_GENERIC_TYPE
    ex.propertyPath() == "/relationships/relation/data"
  }

  def "typed sparse-fieldset mapping keeps writer-owned provenance"() {
    given:
    def base = JsonMapper.builder().build()
    def mapper = JsonApiJackson3.resourceMapper(base)
    def rootType = parameterized(base, GenericResource, GenericThing)
    def thing = new GenericThing("t1", "Thing")
    def root = new GenericResource<GenericThing>("r1", null, thing, [], Optional.empty())
    def selection = RepresentationSelection.builder()
        .include(IncludePath.of("related"))
        .fields("generic-resources", ["value"])
        .build()
    def policy = RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll())

    when:
    def mapped = mapper.toMappedDocument(root, rootType, null, selection, policy)

    then:
    mapped.document().included()*.id() == ["t1"]
    mapped.sparseFieldsetLinkageExemptions().size() == 1
    mapped.document().data().resource().attributes().attributes().keySet() == ["value"] as Set
  }

  private static JsonApiResourceMapper mapper() {
    JsonApiJackson3.resourceMapper(JsonMapper.builder().build())
  }

  private static JavaType parameterized(JsonMapper mapper, Class<?> rawType, Class<?> argument) {
    mapper.typeFactory.constructParametricType(rawType, argument)
  }
}
