package io.github.kazemek.jsonapi.jackson3.internal;

import io.github.kazemek.jsonapi.core.model.JsonApiDocument;
import io.github.kazemek.jsonapi.jackson.document.PrimaryDataKind;
import io.github.kazemek.jsonapi.jackson.internal.wire.ReadLocationIndex;
import tools.jackson.core.JsonParser;

/**
 * Token-driven decoder from JSON:API wire forms into public core model types.
 *
 * <p>Does not run aggregate validation; callers validate after construction.
 */
public final class JsonApiWireReader {

  private JsonApiWireReader() {}

  public static JsonApiDocument readDocument(
      JsonParser parser, PrimaryDataKind primaryDataKind, ReadLocationIndex locations) {
    return DocumentWireReader.readDocument(parser, primaryDataKind, locations);
  }
}
