package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.core.JsonParser;
import io.github.kazemek.jsonapi.core.model.JsonApiDocument;
import io.github.kazemek.jsonapi.jackson.document.PrimaryDataKind;
import java.io.IOException;

/**
 * Token-driven decoder from JSON:API wire forms into public core model types.
 *
 * <p>Does not run aggregate validation; callers validate after construction.
 */
public final class JsonApiWireReader {

  private JsonApiWireReader() {}

  public static JsonApiDocument readDocument(
      JsonParser parser, PrimaryDataKind primaryDataKind, ReadLocationIndex locations)
      throws IOException {
    return DocumentWireReader.readDocument(parser, primaryDataKind, locations);
  }
}
