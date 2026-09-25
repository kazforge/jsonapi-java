package com.kazforge.jsonapi.jackson3.contract

import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.core.model.JsonApiDocument
import com.kazforge.jsonapi.document.DocumentReadContext
import com.kazforge.jsonapi.fixtures.contract.BoundTypedEnvelope
import com.kazforge.jsonapi.fixtures.contract.TypedEnvelopeCharacterizationSpec
import com.kazforge.jsonapi.jackson3.JsonApiJackson3
import com.kazforge.jsonapi.mapping.ResourceTypeRegistry
import tools.jackson.databind.json.JsonMapper

/** Jackson 3 binding of the shared typed-envelope characterization contract. */
class Jackson3TypedEnvelopeCharacterizationSpec extends TypedEnvelopeCharacterizationSpec {

  @Override
  protected JsonApi api() {
    JsonApiJackson3.jsonApi(JsonMapper.builder().build())
  }

  @Override
  protected BoundTypedEnvelope bind(JsonApiDocument document, ResourceTypeRegistry registry) {
    def envelope =
        JsonApiJackson3.domainDocumentReader(
        JsonMapper.builder().build(), DocumentReadContext.resourceDefaults(), registry)
        .fromDocument(document)
    new BoundTypedEnvelope(
        envelope.data(),
        envelope.errors(),
        envelope.meta(),
        envelope.jsonapi(),
        envelope.links(),
        envelope.included(),
        envelope.additionalMembers(),
        { Class targetType -> envelope.metaAs(targetType) })
  }
}
