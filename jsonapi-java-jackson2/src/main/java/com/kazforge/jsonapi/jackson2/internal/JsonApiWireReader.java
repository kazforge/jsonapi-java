package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.core.JsonParser;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.jackson.document.PrimaryDataKind;
import com.kazforge.jsonapi.jackson.internal.wire.ReadLocationIndex;
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
