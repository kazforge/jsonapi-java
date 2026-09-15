package com.kazforge.jsonapi.jackson3.internal.codec;

import tools.jackson.databind.module.SimpleModule;

/** Registers streaming serializers for JSON:API document model types. */
public final class JsonApiDocumentModule extends SimpleModule {

  public JsonApiDocumentModule() {
    super("jsonapi-java-document");
    addSerializer(new JsonApiDocumentSerializer());
  }
}
