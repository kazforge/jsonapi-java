package com.kazforge.jsonapi.jackson2

import com.fasterxml.jackson.databind.json.JsonMapper
import com.kazforge.jsonapi.annotation.JsonApiAttribute
import com.kazforge.jsonapi.annotation.JsonApiId
import com.kazforge.jsonapi.annotation.JsonApiResource
import com.kazforge.jsonapi.core.model.Meta
import com.kazforge.jsonapi.core.model.ResourceIdentifier
import com.kazforge.jsonapi.fixtures.TestFixtureResources
import com.kazforge.jsonapi.fixtures.domainpatch.ArticlePatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithAddressPatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleWithMetaPatch
import com.kazforge.jsonapi.fixtures.domainpatch.ArticleMetaPatch
import com.kazforge.jsonapi.fixtures.domainpatch.AuthorMeta
import com.kazforge.jsonapi.fixtures.domainpatch.WholeMetaTargetFixtures
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic
import com.kazforge.jsonapi.jackson.patch.PatchPresence
import spock.lang.Specification
import spock.lang.Unroll

class PatchDtoExtendedBindingSpec extends Specification {

  @Unroll
  def "binds typed DTO #id"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/${resource}.json")

    when:
    def actual = reader.readValue(json, ArticlePatch)

    then:
    actual == expected

    where:
    id | resource | expected
    "relationship-single" | "relationship-single-linkage" | new ArticlePatch("1", PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.present(ResourceIdentifier.of("people", "p1")), PatchPresence.omitted())
    "relationship-null" | "relationship-null-linkage" | new ArticlePatch("1", PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.present(null), PatchPresence.omitted())
    "relationship-empty-collection" | "relationship-empty-collection" | new ArticlePatch("1", PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.present([]))
    "identity-only" | "identity-other-id" | new ArticlePatch("7", PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.omitted(), PatchPresence.omitted())
  }

  @Unroll
  def "rejects typed DTO #id with a mapping diagnostic"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/${resource}.json")

    when:
    reader.readValue(json, targetType)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == expectedDiagnostic
    ex.location().pointer() == expectedPath

    where:
    id | resource | targetType | expectedDiagnostic | expectedPath
    "unknown-attribute" | "attribute-unknown-member" | ArticlePatch.class | MappingDiagnostic.UNKNOWN_PATCH_MEMBER | "/attributes/bogus"
    "unknown-relationship" | "relationship-unknown-member" | ArticlePatch.class | MappingDiagnostic.UNKNOWN_PATCH_MEMBER | "/relationships/bogus"
    "cardinality-mismatch" | "relationship-cardinality-mismatch" | ArticlePatch.class | MappingDiagnostic.RELATIONSHIP_CARDINALITY_MISMATCH | "/relationships/author/data"
    "resource-type-mismatch" | "resource-type-mismatch" | ArticlePatch.class | MappingDiagnostic.RESOURCE_TYPE_MISMATCH | "/type"
    "unknown-resource-meta" | "title-with-meta-source" | WholeMetaTargetFixtures.NoMetaPatch.class | MappingDiagnostic.UNKNOWN_PATCH_MEMBER | "/meta"
    "unknown-relationship-meta" | "author-meta-with-data" | WholeMetaTargetFixtures.NoRelMetaPatch.class | MappingDiagnostic.UNKNOWN_PATCH_MEMBER | "/relationships/author/meta"
    "scalar-meta-target" | "title-with-meta-source" | WholeMetaTargetFixtures.ScalarMetaPatch.class | MappingDiagnostic.INVALID_META_TARGET | "/meta"
    "nested-unknown-member" | "address-unknown-member" | ArticleWithAddressPatch.class | MappingDiagnostic.UNKNOWN_PATCH_MEMBER | "/attributes/address/bogus"
    "meta-conversion-failure" | "meta-source-object" | ArticleWithMetaPatch.class | MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE | "/meta/source"
  }

  def "binds meta on the typed path"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/meta-source-note-author-meta.json")

    when:
    def patch = reader.readValue(json, ArticleWithMetaPatch)

    then:
    patch == new ArticleWithMetaPatch("1", PatchPresence.present("T"), PatchPresence.present(ResourceIdentifier.of("people", "p1")), PatchPresence.present(new ArticleMetaPatch(PatchPresence.present("cms"), PatchPresence.present("n"))), PatchPresence.present(new AuthorMeta("Alice")))
  }

  def "binds relationship meta on the typed path"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/author-meta-with-data.json")

    when:
    def patch = reader.readValue(json, ArticleWithMetaPatch)

    then:
    patch.authorMeta() == PatchPresence.present(new AuthorMeta("Alice"))
  }

  def "binds whole-linkage identifier meta on the typed path"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/author-identifier-meta.json")

    when:
    def patch = reader.readValue(json, ArticlePatch)

    then:
    patch.author() == PatchPresence.present(new ResourceIdentifier("people", "p1", null, Meta.of([role: "editor"]), Map.of()))

    when:
    def commentsPatch = reader.readValue(TestFixtureResources.readCorpusUtf8("patch/comments-identifier-meta.json"), ArticlePatch)

    then:
    commentsPatch.comments() == PatchPresence.present([
      new ResourceIdentifier("comments", "c1", null, Meta.of([pinned: true]), Map.of()),
      ResourceIdentifier.of("comments", "c2")
    ])
  }

  def "optional inner null binds to present empty"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/subtitle-explicit-null.json")

    when:
    def patch = reader.readValue(json, OptionalSubtitlePatch)

    then:
    patch.subtitle == PatchPresence.present(Optional.empty())
  }

  def "escaped member names produce escaped locations"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())

    when:
    reader.readValue(
        '{"data":{"type":"articles","id":"1","attributes":{"address":{"external/name":"x"}}}}',
        ArticleWithAddressPatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.UNKNOWN_PATCH_MEMBER
    ex.location().pointer() == "/attributes/address/external~1name"
  }

  def "identifier construction failure reclassifies to identifier diagnostic"() {
    given:
    def reader = JsonApiJackson2.patchDtoReader(JsonMapper.builder().build())
    def json = TestFixtureResources.readCorpusUtf8("patch/identifier-not-an-integer.json")

    when:
    reader.readValue(json, IntArticlePatch)

    then:
    def ex = thrown(JsonApiMappingException)
    ex.diagnostic() == MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED
    ex.location().pointer() == "/id"
  }

  @JsonApiResource(type = "articles")
  static class IntArticlePatch {
    @JsonApiId Integer id
    @JsonApiAttribute PatchPresence<String> title
  }

  @JsonApiResource(type = "articles")
  static class OptionalTitlePatch {
    @JsonApiId String id
    @JsonApiAttribute PatchPresence<Optional<String>> title
  }

  @JsonApiResource(type = "articles")
  static class OptionalSubtitlePatch {
    @JsonApiId String id
    @JsonApiAttribute PatchPresence<Optional<String>> subtitle
  }
}
