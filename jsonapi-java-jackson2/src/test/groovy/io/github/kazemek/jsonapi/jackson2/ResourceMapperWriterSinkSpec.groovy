package io.github.kazemek.jsonapi.jackson2

import io.github.kazemek.jsonapi.core.model.ResourceIdentity
import io.github.kazemek.jsonapi.jackson.mapping.MappedDocument
import io.github.kazemek.jsonapi.jackson.representation.IncludePath
import io.github.kazemek.jsonapi.jackson.representation.IncludePolicy
import io.github.kazemek.jsonapi.jackson.representation.RepresentationPolicy
import io.github.kazemek.jsonapi.jackson.representation.RepresentationSelection
import io.github.kazemek.jsonapi.fixtures.domainwrite.Article
import io.github.kazemek.jsonapi.fixtures.domainwrite.Comment
import io.github.kazemek.jsonapi.fixtures.domainwrite.Person
import spock.lang.Shared
import spock.lang.Specification
import com.fasterxml.jackson.databind.json.JsonMapper

// Representative mapped-document composition through every existing Jackson 2 writer sink: a
// sparse-fieldset mapping that traverses an omitted linking relationship must validate and write
// identically through each sink, with the writer composing the linkage-exemption provenance.
class ResourceMapperWriterSinkSpec extends Specification {

  @Shared
  JsonApiResourceMapper mapper = JsonApiJackson2.resourceMapper(JsonMapper.builder().build())

  def "mapped documents compose provenance through every writer sink"() {
    given:
    def mapped = mappedDocument()
    def writer = JsonApiJackson2.writer(JsonMapper.builder().build())
    def expected =
        '{"data":{"type":"articles","id":"1","attributes":{"title":"Title"}},"included":[{"type":"people","id":"9","attributes":{"name":"Dan"}}]}'

    when:
    def string = writer.writeValueAsString(mapped)
    def bytes = writer.writeValueAsBytes(mapped)
    def out = new ByteArrayOutputStream()
    writer.writeValue(out, mapped)
    def stringWriter = new StringWriter()
    writer.writeValue(stringWriter, mapped)
    def buffer = new ByteArrayOutputStream()
    def generator = JsonMapper.builder().build().createGenerator(buffer)
    writer.writeValue(generator, mapped)
    generator.close()

    then:
    string == expected
    new String(bytes, "UTF-8") == expected
    out.toString() == expected
    stringWriter.toString() == expected
    buffer.toString() == expected
  }

  def "mapped documents without exemptions validate like ordinary documents"() {
    given:
    def mapped = mapper.toMappedDocument(
        new Article("1", "Title", "Body", List.of(), new Person("9", "Dan")),
        null,
        RepresentationSelection.builder().include(IncludePath.of("author")).build(),
        RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()))
    def writer = JsonApiJackson2.writer(JsonMapper.builder().build())

    when:
    def json = writer.writeValueAsString(mapped)

    then:
    json.contains('"author":{"data":{"type":"people","id":"9"}}')
    json.contains('"included"')
  }

  private MappedDocument mappedDocument() {
    def mapped = mapper.toMappedDocument(
        new Article("1", "Title", "Body", List.of(comment5()), new Person("9", "Dan")),
        null,
        RepresentationSelection.builder()
        .include(IncludePath.of("author"))
        .fields("articles", "title")
        .build(),
        RepresentationPolicy.defaults().withIncludePolicy(IncludePolicy.allowAll()))
    assert mapped.sparseFieldsetLinkageExemptions() ==
    Set.of(ResourceIdentity.ofId("people", "9"))
    return mapped
  }

  private static Comment comment5() {
    new Comment("5", "First!", new Person("2", "Ezra"))
  }
}
