package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.jackson.document.PrimaryDataKind;
import com.kazforge.jsonapi.jackson.internal.wire.ReadLocationIndex;
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
