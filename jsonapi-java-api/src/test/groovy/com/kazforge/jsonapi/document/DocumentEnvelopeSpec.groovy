package com.kazforge.jsonapi.document

import com.kazforge.jsonapi.core.model.JsonApiObject
import com.kazforge.jsonapi.core.model.Links
import com.kazforge.jsonapi.core.model.Meta
import spock.lang.Specification

class DocumentEnvelopeSpec extends Specification {

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
