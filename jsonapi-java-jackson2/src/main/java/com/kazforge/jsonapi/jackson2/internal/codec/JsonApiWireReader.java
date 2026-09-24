package com.kazforge.jsonapi.jackson2.internal.codec;

import com.fasterxml.jackson.core.JsonParser;
import com.kazforge.jsonapi.core.aggregate.ValidationContext;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.document.PrimaryDataKind;
import java.io.IOException;

/**
 * Token-driven decoder from JSON:API wire forms into public core model types.
 *
 * <p>Does not run aggregate validation; callers validate after construction. Unknown structural
 * members are discarded on read using the bound {@link ValidationContext}; recognized standard,
 * extension, allowed-profile, and {@code @} members decode as today.
 */
public final class JsonApiWireReader {

  private JsonApiWireReader() {}

  public static JsonApiDocument readDocument(
      JsonParser parser,
      PrimaryDataKind primaryDataKind,
      ValidationContext validationContext,
      ReadLocationIndex locations)
      throws IOException {
    return DocumentWireReader.readDocument(parser, primaryDataKind, validationContext, locations);
  }
}
