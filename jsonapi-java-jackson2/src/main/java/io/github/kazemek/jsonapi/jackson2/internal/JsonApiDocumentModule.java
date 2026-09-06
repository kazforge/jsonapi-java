package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.module.SimpleModule;

/** Registers streaming serializers for JSON:API document model types. */
public final class JsonApiDocumentModule extends SimpleModule {

  public JsonApiDocumentModule() {
    super("jsonapi-java-document");
    addSerializer(new JsonApiDocumentSerializer());
  }
}
