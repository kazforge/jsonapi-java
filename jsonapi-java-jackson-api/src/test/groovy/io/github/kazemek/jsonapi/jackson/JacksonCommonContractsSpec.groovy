package io.github.kazemek.jsonapi.jackson

import io.github.kazemek.jsonapi.core.model.DocumentData
import io.github.kazemek.jsonapi.core.model.JsonApiDocument
import io.github.kazemek.jsonapi.core.model.JsonApiObject
import io.github.kazemek.jsonapi.core.model.Links
import io.github.kazemek.jsonapi.core.model.Meta
import io.github.kazemek.jsonapi.core.model.ResourceIdentity
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier
import io.github.kazemek.jsonapi.core.validation.ValidationContext
import io.github.kazemek.jsonapi.core.validation.ValidationRuleCode
import io.github.kazemek.jsonapi.jackson.diagnostic.CodecFailureCategory
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiDocumentReadException
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingLocation
import io.github.kazemek.jsonapi.jackson.diagnostic.SourceLocation
import io.github.kazemek.jsonapi.jackson.document.DocumentEnvelope
import io.github.kazemek.jsonapi.jackson.document.DocumentReadContext
import io.github.kazemek.jsonapi.jackson.document.PrimaryDataKind
import io.github.kazemek.jsonapi.jackson.mapping.DomainData
import io.github.kazemek.jsonapi.jackson.mapping.IdentifierConverter
import io.github.kazemek.jsonapi.jackson.mapping.IncludedResources
import io.github.kazemek.jsonapi.jackson.mapping.MappedDocument
import io.github.kazemek.jsonapi.jackson.mapping.RelationshipLinkage
import io.github.kazemek.jsonapi.jackson.patch.PatchChange
import io.github.kazemek.jsonapi.jackson.patch.PatchCommand
import io.github.kazemek.jsonapi.jackson.patch.PatchPresence
import io.github.kazemek.jsonapi.jackson.patch.StructuredMember
import io.github.kazemek.jsonapi.jackson.patch.StructuredMemberState
import io.github.kazemek.jsonapi.jackson.patch.StructuredPatch
import io.github.kazemek.jsonapi.jackson.representation.FieldAllowance
import io.github.kazemek.jsonapi.jackson.representation.FieldPolicy
import io.github.kazemek.jsonapi.jackson.representation.IncludePath
import io.github.kazemek.jsonapi.jackson.representation.IncludePolicy
import io.github.kazemek.jsonapi.jackson.representation.RepresentationPolicy
import io.github.kazemek.jsonapi.jackson.representation.RepresentationSelection
import io.github.kazemek.jsonapi.jackson.representation.RelationshipAllowance
import spock.lang.Specification

class JacksonCommonContractsSpec extends Specification {

  // RepresentationSelection / RepresentationPolicy

  def "selection none and policy defaults preserve no inclusion and unrestricted fields"() {
    when:
    def selection = RepresentationSelection.none()
    def policy = RepresentationPolicy.defaults()

    then:
    selection.includePaths().isEmpty()
    !selection.includeRequested()
    selection.fieldsets().isEmpty()
    policy.includePolicy() == IncludePolicy.denyAll()
    policy.maxIncludeDepth() == 10
    policy.maxIncludedResources() == 100
    policy.fieldPolicy() == FieldPolicy.allowAll()
  }

  def "selection preserves absent versus explicit empty include requests"() {
    given:
    def explicitEmpty = RepresentationSelection.builder().includeRequested().build()
    def explicitPath = RepresentationSelection.builder().include("comments").build()

    expect:
    !RepresentationSelection.none().includeRequested()
    explicitEmpty.includeRequested()
    explicitEmpty.includePaths().isEmpty()
    explicitEmpty != RepresentationSelection.none()
    explicitPath.includeRequested()
  }

  def "negative limits are rejected"() {
    when:
    RepresentationPolicy.defaults().withMaxIncludeDepth(-1)

    then:
    thrown(IllegalArgumentException)

    when:
    RepresentationPolicy.defaults().withMaxIncludedResources(-1)

    then:
    thrown(IllegalArgumentException)
  }

  def "selection isolates fieldsets and preserves explicit empty fieldsets"() {
    given:
    def mutableFields = new ArrayList<>(["title", "title", "author"])
    def selection = RepresentationSelection.builder().fields("articles", mutableFields).build()

    when:
    mutableFields.add("body-text")

    then:
    selection.fieldsets() == [articles: ["title", "author"]]
    selection.fieldsets()["articles"] == ["title", "author"]
    RepresentationSelection.builder().fields("articles", []).build().fieldsets() == [articles: []]
  }

  def "selection and policy derivations preserve independent values"() {
    given:
    def path = IncludePath.of("comments.author")
    def includePolicy = IncludePolicy.allowing(Set.of(RelationshipAllowance.of("articles", "author")))
    def fieldPolicy = FieldPolicy.allowing(Set.of(FieldAllowance.of("articles", "title")))

    when:
    def selection = RepresentationSelection.builder()
        .include(path)
        .fields("articles", ["title"])
        .build()
    def policy = RepresentationPolicy.defaults()
        .withIncludePolicy(includePolicy)
        .withMaxIncludeDepth(2)
        .withMaxIncludedResources(3)
        .withFieldPolicy(fieldPolicy)

    then:
    selection.includePaths() == [path]
    selection.fieldsets() == [articles: ["title"]]
    policy.includePolicy().is(includePolicy)
    policy.maxIncludeDepth() == 2
    policy.maxIncludedResources() == 3
    policy.fieldPolicy().is(fieldPolicy)
  }

  def "selection convenience methods merge fields deterministically and compare by value"() {
    given:
    def first = RepresentationSelection.builder()
        .include("comments.author")
        .fields("articles", "title", "comments")
        .fields("articles", "title", "author")
        .build()
    def equal = RepresentationSelection.builder()
        .include(IncludePath.of("comments.author"))
        .fields("articles", ["title", "comments", "author"])
        .build()

    expect:
    first.includePaths() == [
      IncludePath.of("comments.author")
    ]
    first.fieldsets() == [articles: ["title", "comments", "author"]]
    first == equal
    first.hashCode() == equal.hashCode()
    first != RepresentationSelection.none()
    first != (Object) "selection"
  }

  // IncludePath

  def "factory-time malformed paths fail with raw input in the message and no location"() {
    when:
    IncludePath.of(input)

    then:
    def e = thrown(JsonApiMappingException)
    e.diagnostic() == MappingDiagnostic.INVALID_INCLUDE_PATH
    e.resourceClass() == null
    e.location() == null
    e.message.contains(input)

    where:
    input << [
      "",
      ".a",
      "a.",
      "a..b",
      " ",
      "a. .b"
    ]
  }

  def "canonical constructor rejects whitespace and dotted segments"() {
    when:
    new IncludePath(List.of(segment))

    then:
    def e = thrown(JsonApiMappingException)
    e.diagnostic() == MappingDiagnostic.INVALID_INCLUDE_PATH
    e.resourceClass() == null

    where:
    segment << [" ", "comments.author"]
  }

  def "dotted forms round-trip segments"() {
    expect:
    IncludePath.of("comments.author").segments() == ["comments", "author"]
    IncludePath.of("comments.author").dotted() == "comments.author"
    IncludePath.of("comments.author").dottedThrough(1) == "comments.author"
    IncludePath.of("comments.author").dottedThrough(0) == "comments"
  }

  // IncludePolicy / RelationshipAllowance

  def "equivalent policies compare equal"() {
    expect:
    IncludePolicy.denyAll() == IncludePolicy.denyAll()
    IncludePolicy.allowAll() == IncludePolicy.allowAll()
    IncludePolicy.allowing(Set.of(RelationshipAllowance.of("articles", "author"))) ==
        IncludePolicy.allowing(Set.of(RelationshipAllowance.of("articles", "author")))
  }

  def "policy modes gate relationship traversal"() {
    given:
    def allowance = RelationshipAllowance.of("articles", "author")

    expect:
    !IncludePolicy.denyAll().allows("articles", "author")
    IncludePolicy.allowAll().allows("articles", "author")
    IncludePolicy.allowing(Set.of(allowance)).allows("articles", "author")
    !IncludePolicy.allowing(Set.of(allowance)).allows("articles", "comments")
  }

  def "allowing policy defensively copies its allowance set"() {
    given:
    def mutable = new HashSet<RelationshipAllowance>([
      RelationshipAllowance.of("articles", "author")
    ])

    when:
    def policy = IncludePolicy.allowing(mutable)
    mutable.add(RelationshipAllowance.of("articles", "comments"))

    then:
    !policy.allows("articles", "comments")
    policy.allows("articles", "author")
  }

  // FieldPolicy / FieldAllowance

  def "field policy modes gate fieldset fields"() {
    given:
    def allowance = FieldAllowance.of("articles", "title")

    expect:
    !FieldPolicy.denyAll().allows("articles", "title")
    FieldPolicy.allowAll().allows("articles", "title")
    FieldPolicy.allowing(Set.of(allowance)).allows("articles", "title")
    !FieldPolicy.allowing(Set.of(allowance)).allows("articles", "author")
  }

  def "allowing field policy defensively copies its allowance set"() {
    given:
    def mutable = new HashSet<FieldAllowance>([
      FieldAllowance.of("articles", "title")
    ])

    when:
    def policy = FieldPolicy.allowing(mutable)
    mutable.add(FieldAllowance.of("articles", "author"))

    then:
    !policy.allows("articles", "author")
    policy.allows("articles", "title")
  }

  // DomainData

  def "resource collection is defensively copied and unmodifiable"() {
    given:
    def source = ["x"] as List<Object>

    when:
    def collection = new DomainData.ResourceCollection(source)
    source.add("y")

    then:
    collection.resources() == ["x"]

    when:
    collection.resources().add("z")
    then:
    thrown(UnsupportedOperationException)
  }

  def "domain data preserves explicit null, resource, and identifier primary-data states"() {
    given:
    def resource = "article dto"
    def identifier = ResourceIdentifier.of("articles", "1")
    def resources = new ArrayList<Object>([resource])
    def identifiers = new ArrayList<ResourceIdentifier>([identifier])

    when:
    def collection = new DomainData.ResourceCollection(resources)
    def identifierCollection = new DomainData.IdentifierCollection(identifiers)
    resources.add("another dto")
    identifiers.add(ResourceIdentifier.of("articles", "2"))

    then:
    (Set) DomainData.class.getPermittedSubclasses().toSet() ==
        [
          DomainData.NullData,
          DomainData.SingleResource,
          DomainData.ResourceCollection,
          DomainData.SingleIdentifier,
          DomainData.IdentifierCollection
        ].toSet()
    DomainData.NullData.INSTANCE == new DomainData.NullData()
    new DomainData.SingleResource(resource).resource().is(resource)
    collection.resources() == [resource]
    new DomainData.SingleIdentifier(identifier).identifier() == identifier
    identifierCollection.identifiers() == [identifier]

    when:
    collection.resources().add("nope")

    then:
    thrown(UnsupportedOperationException)
  }

  def "domain data rejects null required payloads and collection members"() {
    when:
    new DomainData.SingleResource(null)

    then:
    thrown(NullPointerException)

    when:
    new DomainData.ResourceCollection([null])

    then:
    thrown(NullPointerException)

    when:
    new DomainData.SingleIdentifier(null)

    then:
    thrown(NullPointerException)

    when:
    new DomainData.IdentifierCollection([null])

    then:
    thrown(NullPointerException)
  }

  def "document envelope preserves independent absence and present document members"() {
    given:
    def links = Links.empty()
    def meta = Meta.empty()
    def jsonapi = JsonApiObject.ofVersion("1.1")

    expect:
    new DocumentEnvelope(null, null, null) == new DocumentEnvelope(null, null, null)
    new DocumentEnvelope(links, null, null).links() == links
    new DocumentEnvelope(null, meta, null).meta() == meta
    new DocumentEnvelope(null, null, jsonapi).jsonapi() == jsonapi
    new DocumentEnvelope(links, meta, jsonapi) != new DocumentEnvelope(null, meta, jsonapi)
  }

  // IncludedResources

  def "of preserves wire order and resolves identities through declared positions"() {
    given:
    def dto = "dto"
    def included =
        IncludedResources.of(
        ["x", dto] as List<Object>,
        [
          [
            ResourceIdentity.ofId("people", "9"),
            ResourceIdentity.ofLid("people", "9")
          ] as Set<ResourceIdentity>,
          [] as Set<ResourceIdentity>
        ] as List<Set<ResourceIdentity>>)

    expect:
    included.resources() == ["x", "dto"]
    included.find(ResourceIdentity.ofId("people", "9")).get() == "x"
    included.find(ResourceIdentity.ofLid("people", "9")).get() == "x"
    included.find(ResourceIdentity.ofId("people", "99")).isEmpty()
  }

  def "identity resolves only to the position that declared it"() {
    given:
    def first = "first"
    def second = "second"
    def included =
        IncludedResources.of(
        [first, second] as List<Object>,
        [
          [
            ResourceIdentity.ofId("people", "9")
          ] as Set<ResourceIdentity>,
          [
            ResourceIdentity.ofId("people", "2")
          ] as Set<ResourceIdentity>
        ] as List<Set<ResourceIdentity>>)

    expect:
    included.find(ResourceIdentity.ofId("people", "9")).get() == first
    included.find(ResourceIdentity.ofId("people", "2")).get() == second
    // an identity never declared anywhere resolves empty, never a non-declaring position
    included.find(ResourceIdentity.ofId("people", "99")).isEmpty()
  }

  def "of rejects identity declarations that do not match the resource count"() {
    when:
    IncludedResources.of(
        ["x"] as List<Object>,
        [
          [
            ResourceIdentity.ofId("people", "9")
          ] as Set<ResourceIdentity>,
          [] as Set<ResourceIdentity>
        ] as List<Set<ResourceIdentity>>)

    then:
    thrown(IllegalArgumentException)

    when:
    IncludedResources.of(
        ["x", "y"] as List<Object>,
        [
          [
            ResourceIdentity.ofId("people", "9")
          ] as Set<ResourceIdentity>
        ])

    then:
    thrown(IllegalArgumentException)
  }

  def "of rejects the same identity declared for more than one position"() {
    when:
    IncludedResources.of(
        ["x", "y"] as List<Object>,
        [
          [
            ResourceIdentity.ofId("people", "9")
          ] as Set<ResourceIdentity>,
          [
            ResourceIdentity.ofId("people", "9")
          ] as Set<ResourceIdentity>
        ] as List<Set<ResourceIdentity>>)

    then:
    thrown(IllegalArgumentException)
  }

  def "of defensively copies construction sources"() {
    given:
    def sourceList = ["x"] as List<Object>
    def mutableIdentities = new LinkedHashSet<ResourceIdentity>([
      ResourceIdentity.ofId("people", "9")
    ])
    List<Set<ResourceIdentity>> sourceIdentities = [mutableIdentities]

    when:
    def included = IncludedResources.of(sourceList, sourceIdentities)
    sourceList.add("y")
    mutableIdentities.add(ResourceIdentity.ofId("people", "99"))

    then:
    included.resources() == ["x"]
    included.find(ResourceIdentity.ofId("people", "9")).get() == "x"
    included.find(ResourceIdentity.ofId("people", "99")).isEmpty()

    when:
    included.resources().add("z")
    then:
    thrown(UnsupportedOperationException)
  }

  // DocumentReadContext

  def "read context defaults and derivations"() {
    expect:
    DocumentReadContext.resourceDefaults().primaryDataKind() == PrimaryDataKind.RESOURCE
    DocumentReadContext.identifierDefaults().primaryDataKind() == PrimaryDataKind.RESOURCE_IDENTIFIER
    DocumentReadContext.of(ValidationContext.defaults(), PrimaryDataKind.RESOURCE) ==
        DocumentReadContext.resourceDefaults()
    DocumentReadContext.resourceDefaults()
        .withPrimaryDataKind(PrimaryDataKind.RESOURCE_IDENTIFIER) ==
        DocumentReadContext.identifierDefaults()
  }

  def "read context rejects missing policy components"() {
    when:
    new DocumentReadContext(null, PrimaryDataKind.RESOURCE)

    then:
    thrown(NullPointerException)

    when:
    new DocumentReadContext(ValidationContext.defaults(), null)

    then:
    thrown(NullPointerException)
  }

  // MappedDocument

  def "mapped document carries defensive-copy linkage exemptions"() {
    given:
    def document =
        new JsonApiDocument(DocumentData.NullData.INSTANCE, null, null, null, null, null, Map.of())
    def mutable = new LinkedHashSet<ResourceIdentity>([
      ResourceIdentity.ofId("people", "9")
    ])

    when:
    def mapped = new MappedDocument(document, mutable)
    mutable.add(ResourceIdentity.ofId("people", "10"))

    then:
    mapped.document().is(document)
    mapped.sparseFieldsetLinkageExemptions() ==
        Set.of(ResourceIdentity.ofId("people", "9")) as Set

    when:
    new MappedDocument(document, null)

    then:
    thrown(NullPointerException)

    when:
    new MappedDocument(document, Collections.singleton(null))

    then:
    thrown(NullPointerException)
  }

  // IdentifierConverter

  def "defaults converter delegates to toString and parse returns the wire string"() {
    given:
    def converter = IdentifierConverter.defaults()

    expect:
    converter.convert(42L) == "42"
    converter.convert(null) == null
    converter.parse("9") == "9"
    converter.parse(null) == null
  }

  // Diagnostics and source location

  def "mapping exception carries stable diagnostic, class, and pointer-form location"() {
    when:
    def e = new JsonApiMappingException(
        MappingDiagnostic.MISSING_IDENTIFIER,
        String,
        MappingLocation.of("id"),
        "message")

    then:
    e.diagnostic() == MappingDiagnostic.MISSING_IDENTIFIER
    e.resourceClass() == String
    e.location() == MappingLocation.parse("/id")
    e.propertyPath() == "/id"
    e.message == "message"
  }

  def "mapping exception represents absent location as null, never empty or root"() {
    when:
    def e = JsonApiMappingException.withoutLocation(
        MappingDiagnostic.MISSING_RESOURCE_ANNOTATION, String, "message")

    then:
    e.location() == null
    e.propertyPath() == null
    e.resourceClass() == String

    when:
    new JsonApiMappingException(
        MappingDiagnostic.INVALID_RESOURCE_TYPE,
        null,
        MappingLocation.parse("/"),
        "root")

    then:
    thrown(IllegalArgumentException)
  }

  def "read exception carries category, pointer, location, and rule code"() {
    given:
    def location = new SourceLocation(1, 2, 3L, 4L)

    when:
    def e = new JsonApiDocumentReadException(
        CodecFailureCategory.MALFORMED_JSON, "/data", location, "message")

    then:
    e.category() == CodecFailureCategory.MALFORMED_JSON
    e.jsonPointer() == "/data"
    e.sourceLocation() == location
    e.ruleCode() == null
    e.message == "message"
  }

  def "diagnostic exceptions retain their stable context and optional causes"() {
    given:
    def mappingCause = new IllegalStateException("mapping cause")
    def readCause = new IllegalArgumentException("read cause")
    def location = MappingLocation.of("attributes", "title")
    def sourceLocation = new SourceLocation(1, 2, 3L, 4L)

    when:
    def mapping = new JsonApiMappingException(
        MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE, String, location, "mapping", mappingCause)
    def defaultMessage = new JsonApiMappingException(
        MappingDiagnostic.MISSING_IDENTIFIER, String, location)
    def read = new JsonApiDocumentReadException(
        CodecFailureCategory.LOCAL_VALIDATION,
        "/data",
        sourceLocation,
        ValidationRuleCode.MISSING_RESOURCE_ID,
        "read",
        readCause)

    then:
    mapping.diagnostic() == MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE
    mapping.resourceClass() == String
    mapping.location() == location
    mapping.getCause().is(mappingCause)
    defaultMessage.message == "MISSING_IDENTIFIER"
    read.category() == CodecFailureCategory.LOCAL_VALIDATION
    read.jsonPointer() == "/data"
    read.sourceLocation() == sourceLocation
    read.ruleCode() == ValidationRuleCode.MISSING_RESOURCE_ID
    read.getCause().is(readCause)
  }

  def "source location distinguishes known and unknown positions"() {
    expect:
    !SourceLocation.UNKNOWN.isKnown()
    new SourceLocation(0, 0, 0L, 0L).isKnown()
    new SourceLocation(-1, 2, -1L, -1L).isKnown()
  }

  // PatchCommand / PatchChange

  def "patch change compact constructors reject null names"() {
    when:
    new PatchChange.AttributeChange(null, "title", "x")

    then:
    thrown(NullPointerException)

    when:
    new PatchChange.AttributeChange("title", null, "x")

    then:
    thrown(NullPointerException)

    when:
    new PatchChange.RelationshipChange(null, "author", null)

    then:
    thrown(NullPointerException)

    when:
    new PatchChange.RelationshipChange("author", null, null)

    then:
    thrown(NullPointerException)
  }

  def "patch change sealed variants preserve explicit null and variant identity"() {
    given:
    def changes = [
      new PatchChange.AttributeChange("title", "title", null),
      new PatchChange.RelationshipChange("author", "author", null),
      new PatchChange.ResourceMetaChange("meta", "articleMeta", null),
      new PatchChange.RelationshipMetaChange("author", "authorMeta", null)
    ]

    expect:
    (Set) PatchChange.class.getPermittedSubclasses().toSet() ==
        [
          PatchChange.AttributeChange,
          PatchChange.RelationshipChange,
          PatchChange.ResourceMetaChange,
          PatchChange.RelationshipMetaChange
        ].toSet()
    changes*.value() == [null, null, null, null]
    changes*.jsonapiName() == [
      "title",
      "author",
      "meta",
      "author"
    ]
    changes*.logicalName() == [
      "title",
      "author",
      "articleMeta",
      "authorMeta"
    ]
  }

  def "patch command rejects null components"() {
    when:
    new PatchCommand<>(null, "1", List.of())

    then:
    thrown(NullPointerException)

    when:
    new PatchCommand<>(String, null, List.of())

    then:
    thrown(NullPointerException)

    when:
    new PatchCommand<>(String, "1", null)

    then:
    thrown(NullPointerException)

    when:
    new PatchCommand<>(String, "1", [null])

    then:
    thrown(NullPointerException)
  }

  def "mutating changes or list or set or array values cannot affect the command"() {
    given:
    def mutableList = new ArrayList<>(["a"])
    def mutableSet = new LinkedHashSet<>(["s"])
    def mutableArray = ["x"] as String[]
    def changes = new ArrayList<PatchChange>()
    changes.add(new PatchChange.AttributeChange("title", "title", mutableList))
    changes.add(new PatchChange.AttributeChange("tags", "tags", mutableSet))
    changes.add(new PatchChange.AttributeChange("names", "names", mutableArray))
    def command = new PatchCommand<>(String, "1", changes)

    when:
    changes.add(new PatchChange.AttributeChange("extra", "extra", "y"))
    mutableList.add("b")
    mutableSet.add("t")
    mutableArray[0] = "z"
    command.changes().add(new PatchChange.AttributeChange("nope", "nope", "n"))

    then:
    thrown(UnsupportedOperationException)
    command.changes().size() == 3
    (command.changes()[0].value() as List) == ["a"]
    (command.changes()[1].value() as Set) == ["s"] as Set
    (command.changes()[2].value() as String[])[0] == "x"
  }

  def "mutating an array returned from value cannot affect the stored change"() {
    given:
    def change = new PatchChange.AttributeChange("names", "names", ["a"] as String[])
    def exposed = change.value() as String[]

    when:
    exposed[0] = "changed"

    then:
    (change.value() as String[])[0] == "a"
  }

  def "mutating a map value cannot affect the stored change"() {
    given:
    def mutableMap = new LinkedHashMap<String, Object>()
    mutableMap.put("type", "tags")
    mutableMap.put("id", "t1")
    mutableMap.put("lid", null)
    def change = new PatchChange.RelationshipChange("author", "author", mutableMap)

    when:
    mutableMap.put("id", "mutated")
    (change.value() as Map).put("id", "exposed")

    then:
    thrown(UnsupportedOperationException)
    (change.value() as Map).id == "t1"
    (change.value() as Map).type == "tags"
  }

  def "mutating a primitive array returned from value cannot affect the stored change"() {
    given:
    def change = new PatchChange.RelationshipChange("counts", "counts", [1, 2] as int[])
    def exposed = change.value() as int[]

    when:
    exposed[0] = 99

    then:
    (change.value() as int[])[0] == 1
    (change.value() as int[])[1] == 2
  }

  // PatchPresence

  def "patch presence tri-state distinguishes omitted, present value, and present null"() {
    expect:
    PatchPresence.omitted().isOmitted()
    !PatchPresence.present("v").isOmitted()
    !PatchPresence.present(null).isOmitted()
    PatchPresence.present("v").value() == "v"
    PatchPresence.present(null).value() == null
  }

  def "patch presence states compare by value across record variants"() {
    expect:
    PatchPresence.omitted() == new PatchPresence.Omitted()
    PatchPresence.present("v") == new PatchPresence.Present("v")
    PatchPresence.present(null) == new PatchPresence.Present(null)
    PatchPresence.omitted() != PatchPresence.present(null)
    PatchPresence.present("v") != PatchPresence.present(null)
  }

  def "patch presence is exhaustively matchable over omitted and present"() {
    given:
    def inputs = [
      PatchPresence.omitted(),
      PatchPresence.present("v"),
      PatchPresence.present(null)
    ]

    expect:
    // The sealed contract must stay exactly the two variants exercised below; a newly permitted
    // implementation fails this assertion until it is deliberately handled in the switch.
    (Set) PatchPresence.class.getPermittedSubclasses().toSet() ==
        [
          PatchPresence.Omitted,
          PatchPresence.Present
        ].toSet()
    inputs.every { presence ->
      switch (presence) {
        case PatchPresence.Omitted:
          return presence.isOmitted()
        case PatchPresence.Present:
          return true
      }
    }
  }

  def "patch presence keeps nullable Optional as a separate inner concern"() {
    given:
    def omitted = PatchPresence.<Optional<String>>omitted()
    def empty = PatchPresence.present(Optional.empty())
    def nulled = PatchPresence.present(null)

    expect:
    omitted.isOmitted()
    !empty.isOmitted()
    !nulled.isOmitted()
    empty.value() == Optional.empty()
    nulled.value() == null
  }

  def "relationship linkage requires a non-null target and allows null meta"() {
    when:
    new RelationshipLinkage<String, String>(null, "meta")

    then:
    thrown(NullPointerException)

    when:
    def linkage = new RelationshipLinkage<String, String>("target", null)

    then:
    linkage.target() == "target"
    linkage.meta() == null
    linkage == new RelationshipLinkage<String, String>("target", null)
    linkage != new RelationshipLinkage<String, String>("target", "meta")
  }

  // StructuredPatch / StructuredMember / StructuredMemberState (recursive structured-value payload)

  def "structured patch rejects null members and freezes the members list"() {
    when:
    new StructuredPatch(null)

    then:
    thrown(NullPointerException)

    when:
    def members = new ArrayList<StructuredMember>()
    members.add(new StructuredMember("street", "street", new StructuredMemberState.Atomic("S")))
    def patch = new StructuredPatch(members)

    then:
    members.add(new StructuredMember("x", "x", new StructuredMemberState.Atomic("x")))
    patch.members().size() == 1
  }

  def "structured member rejects null names and state"() {
    when:
    new StructuredMember(null, "street", new StructuredMemberState.Atomic("S"))

    then:
    thrown(NullPointerException)

    when:
    new StructuredMember("street", null, new StructuredMemberState.Atomic("S"))

    then:
    thrown(NullPointerException)

    when:
    new StructuredMember("street", "street", null)

    then:
    thrown(NullPointerException)
  }

  def "structured member state has exactly atomic and structured variants"() {
    expect:
    (Set) StructuredMemberState.class.getPermittedSubclasses().toSet() ==
        [
          StructuredMemberState.Atomic,
          StructuredMemberState.Structured
        ].toSet()
  }

  def "structured state freezes nested members recursively and atomics shallowly"() {
    given:
    def nestedList = new ArrayList<StructuredMember>()
    nestedList.add(new StructuredMember("lat", "lat", new StructuredMemberState.Atomic("1")))
    def structured = new StructuredMemberState.Structured(nestedList)
    def container = new ArrayList<String>(["a"])
    def atomic = new StructuredMemberState.Atomic(container)

    when:
    nestedList.add(new StructuredMember("lon", "lon", new StructuredMemberState.Atomic("2")))
    container.add("b")

    then:
    structured.members().size() == 1
    atomic.value() == ["a"]

    when:
    def storedArray = new StructuredMemberState.Atomic(["x"] as String[])
    def exposedArray = (String[]) storedArray.value()
    exposedArray[0] = "y"

    then:
    ((String[]) storedArray.value())[0] == "x"
  }

  def "structured patch equality follows nested structure"() {
    expect:
    new StructuredPatch(
        [
          new StructuredMember("street", "street", new StructuredMemberState.Atomic("S"))
        ]) ==
        new StructuredPatch(
        [
          new StructuredMember("street", "street", new StructuredMemberState.Atomic("S"))
        ])
    new StructuredPatch(
        [
          new StructuredMember("street", "street", new StructuredMemberState.Atomic("S"))
        ]) !=
        new StructuredPatch(
        [
          new StructuredMember("city", "city", new StructuredMemberState.Atomic("S"))
        ])
  }
}
