package com.kazforge.jsonapi.document

import com.kazforge.jsonapi.core.model.JsonApiObject
import com.kazforge.jsonapi.core.model.Links
import com.kazforge.jsonapi.core.model.Meta
import spock.lang.Specification

class DocumentEnvelopeSpec extends Specification {

  def "named factories distinguish absent from present-empty members"() {
    expect:
    DocumentEnvelope.empty() == new DocumentEnvelope(null, null, null)
    DocumentEnvelope.ofLinks(Links.empty()) == new DocumentEnvelope(Links.empty(), null, null)
    DocumentEnvelope.ofMeta(Meta.empty()) == new DocumentEnvelope(null, Meta.empty(), null)
    DocumentEnvelope.ofJsonApi(JsonApiObject.empty()) == new DocumentEnvelope(null, null, JsonApiObject.empty())
    DocumentEnvelope.ofLinks(null) == DocumentEnvelope.empty()
    DocumentEnvelope.ofMeta(null) == DocumentEnvelope.empty()
    DocumentEnvelope.ofJsonApi(null) == DocumentEnvelope.empty()
    DocumentEnvelope.ofLinks(Links.empty()) != DocumentEnvelope.empty()
    DocumentEnvelope.ofMeta(Meta.empty()) != DocumentEnvelope.empty()
    DocumentEnvelope.ofJsonApi(JsonApiObject.empty()) != DocumentEnvelope.empty()
  }

  def "envelope copies replace only the named member and null clears it"() {
    given:
    def base = DocumentEnvelope.ofLinks(Links.ofLinks([self: null]))
    .withMeta(Meta.of([missing: null]))
    .withJsonApi(JsonApiObject.ofVersion("1.1"))

    expect:
    base.withLinks(Links.empty()) == new DocumentEnvelope(Links.empty(), base.meta(), base.jsonapi())
    base.withMeta(Meta.empty()) == new DocumentEnvelope(base.links(), Meta.empty(), base.jsonapi())
    base.withJsonApi(JsonApiObject.empty()) == new DocumentEnvelope(base.links(), base.meta(), JsonApiObject.empty())
    base.withLinks(null) == new DocumentEnvelope(null, base.meta(), base.jsonapi())
    base.withMeta(null) == new DocumentEnvelope(base.links(), null, base.jsonapi())
    base.withJsonApi(null) == new DocumentEnvelope(base.links(), base.meta(), null)
    base.withLinks(null).withMeta(null).withJsonApi(null) == DocumentEnvelope.empty()
    base.links().links().containsKey("self")
    base.links().links().self == null
    base.meta().members().containsKey("missing")
    base.meta().members().missing == null
    base.jsonapi() == JsonApiObject.ofVersion("1.1")
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
}
