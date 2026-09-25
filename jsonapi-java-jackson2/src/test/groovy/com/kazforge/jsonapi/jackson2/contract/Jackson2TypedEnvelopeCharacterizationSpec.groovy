package com.kazforge.jsonapi.jackson2.contract

import com.fasterxml.jackson.databind.json.JsonMapper
import com.kazforge.jsonapi.api.JsonApi
import com.kazforge.jsonapi.core.model.JsonApiDocument
import com.kazforge.jsonapi.document.DocumentReadContext
import com.kazforge.jsonapi.fixtures.contract.BoundTypedEnvelope
import com.kazforge.jsonapi.fixtures.contract.TypedEnvelopeCharacterizationSpec
import com.kazforge.jsonapi.jackson2.JsonApiJackson2
import com.kazforge.jsonapi.mapping.ResourceTypeRegistry

/** Jackson 2 binding of the shared typed-envelope characterization contract. */
class Jackson2TypedEnvelopeCharacterizationSpec extends TypedEnvelopeCharacterizationSpec {

  @Override
  protected JsonApi api() {
    JsonApiJackson2.jsonApi(JsonMapper.builder().build())
  }

  @Override
  protected BoundTypedEnvelope bind(JsonApiDocument document, ResourceTypeRegistry registry) {
    def envelope =
        JsonApiJackson2.domainDocumentReader(
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
