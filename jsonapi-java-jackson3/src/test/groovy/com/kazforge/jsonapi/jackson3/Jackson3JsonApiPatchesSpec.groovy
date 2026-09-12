package com.kazforge.jsonapi.jackson3

import com.kazforge.jsonapi.core.validation.DocumentUsage
import com.kazforge.jsonapi.core.validation.ValidationContext
import com.kazforge.jsonapi.jackson.document.DocumentReadContext
import com.kazforge.jsonapi.jackson.document.PrimaryDataKind
import com.kazforge.jsonapi.jackson.patch.PatchPresence
import com.kazforge.jsonapi.fixtures.domainpatch.ArticlePatch
import com.kazforge.jsonapi.fixtures.domainread.FlatArticle
import com.kazforge.jsonapi.jackson3.ParameterizedBindingFixtures.GenericPatch
import spock.lang.Shared
import spock.lang.Specification
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper

class Jackson3JsonApiPatchesSpec extends Specification {

  @Shared
  Jackson3JsonApi jsonApi = JsonApiJackson3.jsonApi(JsonMapper.builder().build())

  def "readPatch binds supplied members while omitted members stay omitted"() {
    given:
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"New title"}}}'

    when:
    def patch = jsonApi.patches().readPatch(json, ArticlePatch)

    then:
    patch.id() == "1"
    patch.title() == PatchPresence.present("New title")
    patch.body().isOmitted()
    patch.author().isOmitted()
    patch.comments().isOmitted()
  }

  def "readPatch keeps explicit null distinct from omitted"() {
    given:
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":null},"relationships":{"author":{"data":null}}}}'

    when:
    def patch = jsonApi.patches().readPatch(json, ArticlePatch)

    then:
    patch.title() == PatchPresence.present(null)
    patch.author() == PatchPresence.present(null)
    patch.body().isOmitted()
  }

  def "readCommand projects only supplied changes with resource identity"() {
    given:
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"New title"},"relationships":{"author":{"data":{"type":"people","id":"p1"}}}}}'

    when:
    def command = jsonApi.patches().readCommand(json, FlatArticle)

    then:
    command.resourceType() == FlatArticle
    command.identity() == "1"
    command.changes().size() == 2
  }

  def "bindPatch and bindCommand reuse an already-validated document"() {
    given:
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"New title"}}}'
    def context = DocumentReadContext.of(
        ValidationContext.defaults().withDocumentUsage(DocumentUsage.UPDATE_REQUEST),
        PrimaryDataKind.RESOURCE)
    def document = jsonApi.documents().read(json, context)
    // A ParameterizedType (not a Class) forces runtime dispatch onto the Type overloads.
    def patchType = new TypeReference<GenericPatch<String>>() {}.getType()

    when:
    def patch = jsonApi.patches().bindPatch(document, ArticlePatch)
    def command = jsonApi.patches().bindCommand(document, FlatArticle)

    then:
    patch == jsonApi.patches().readPatch(json, ArticlePatch)
    command == jsonApi.patches().readCommand(json, FlatArticle)

    when:
    def genericPatch = jsonApi.patches().bindPatch(document, patchType)
    def genericCommand = jsonApi.patches().bindCommand(document, patchType)

    then:
    genericPatch == new GenericPatch("1", PatchPresence.present("New title"))
    genericCommand.resourceType() == GenericPatch
    genericCommand.identity() == "1"
    genericCommand.changes().size() == 1
  }

  def "generic Type overloads bind through full generic fidelity"() {
    given:
    def patchType = new TypeReference<GenericPatch<String>>() {}.getType()
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"New title"}}}'

    when:
    def patch = jsonApi.patches().readPatch(json, patchType)
    def command = jsonApi.patches().readCommand(json, patchType)

    then:
    patch == new GenericPatch("1", PatchPresence.present("New title"))
    command.resourceType() == GenericPatch
    command.identity() == "1"
  }

  def "stream sources mirror string sources without closing caller streams"() {
    given:
    def json = '{"data":{"type":"articles","id":"1","attributes":{"title":"New title"}}}'
    def patchType = new TypeReference<GenericPatch<String>>() {}.getType()

    when:
    def patch = jsonApi.patches().readPatch(new ByteArrayInputStream(json.bytes), ArticlePatch)
    def genericPatch = jsonApi.patches().readPatch(new ByteArrayInputStream(json.bytes), patchType)
    def command = jsonApi.patches().readCommand(new ByteArrayInputStream(json.bytes), FlatArticle)
    def genericCommand = jsonApi.patches().readCommand(new ByteArrayInputStream(json.bytes), patchType)

    then:
    patch == jsonApi.patches().readPatch(json, ArticlePatch)
    genericPatch == new GenericPatch("1", PatchPresence.present("New title"))
    command == jsonApi.patches().readCommand(json, FlatArticle)
    genericCommand == jsonApi.patches().readCommand(json, patchType)
  }
}
