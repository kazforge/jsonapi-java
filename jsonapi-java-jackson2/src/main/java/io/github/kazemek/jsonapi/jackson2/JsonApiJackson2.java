package io.github.kazemek.jsonapi.jackson2;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.kazemek.jsonapi.core.validation.ValidationContext;
import io.github.kazemek.jsonapi.jackson2.internal.JsonApiDocumentModule;
import java.util.Objects;

/**
 * Factory for the Jackson 2 JSON:API document writer.
 *
 * <p>Callers supply an already-configured {@link JsonMapper}. The canonical factory accepts that
 * mapper first, followed by the capability-specific {@link ValidationContext}; the convenience
 * factory selects documented defaults and delegates. Mapper builders are intentionally not
 * accepted, and factory construction never mutates or replaces the caller's configuration in place.
 * The writer derives a codec-configured mapper via {@link JsonMapper#rebuild()} and registers only
 * the internal JSON:API document module. Public surface consists of {@link JsonApiDocumentWriter};
 * additional capabilities follow in later parity stories per ADR-016's semantic cross-major policy.
 */
public final class JsonApiJackson2 {

  private static final String CONTEXT = "context";

  private JsonApiJackson2() {}

  /**
   * Returns a writer that validates with {@link ValidationContext#defaults()} then serializes
   * documents using a derived codec-configured mapper.
   */
  public static JsonApiDocumentWriter writer(JsonMapper base) {
    return writer(base, ValidationContext.defaults());
  }

  /**
   * Returns a writer that validates with the given context then serializes documents using a
   * derived codec-configured mapper.
   */
  public static JsonApiDocumentWriter writer(JsonMapper base, ValidationContext context) {
    Objects.requireNonNull(base, "base");
    Objects.requireNonNull(context, CONTEXT);
    return new JsonApiDocumentWriter(documentMapper(base), context);
  }

  /**
   * Derives a new mapper with JSON:API document serializers registered. Package-private so callers
   * cannot serialize documents without aggregate validation.
   */
  static JsonMapper documentMapper(JsonMapper base) {
    Objects.requireNonNull(base, "base");
    return base.rebuild().addModule(new JsonApiDocumentModule()).build();
  }
}
