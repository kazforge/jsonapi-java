package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.github.kazemek.jsonapi.core.model.JsonApiDocument;
import java.io.IOException;

/** Jackson serializer entry point for {@link JsonApiDocument}. */
final class JsonApiDocumentSerializer extends StdSerializer<JsonApiDocument> {

  JsonApiDocumentSerializer() {
    super(JsonApiDocument.class);
  }

  @Override
  public void serialize(JsonApiDocument value, JsonGenerator gen, SerializerProvider provider)
      throws IOException {
    JsonApiWireWriter.writeDocument(value, gen);
  }
}
