package com.kazforge.jsonapi.mapping

import com.kazforge.jsonapi.core.model.DocumentData
import com.kazforge.jsonapi.core.model.JsonApiDocument
import com.kazforge.jsonapi.core.model.ResourceIdentity
import spock.lang.Specification

class MappedDocumentSpec extends Specification {

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
}
