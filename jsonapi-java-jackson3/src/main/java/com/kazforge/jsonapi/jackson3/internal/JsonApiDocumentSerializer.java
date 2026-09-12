package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.core.model.JsonApiDocument;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/** Jackson serializer entry point for {@link JsonApiDocument}. */
final class JsonApiDocumentSerializer extends ValueSerializer<JsonApiDocument> {

  @Override
  public Class<JsonApiDocument> handledType() {
    return JsonApiDocument.class;
  }

  @Override
  public void serialize(JsonApiDocument value, JsonGenerator gen, SerializationContext ctxt)
      throws JacksonException {
    JsonApiWireWriter.writeDocument(value, gen);
  }
}
