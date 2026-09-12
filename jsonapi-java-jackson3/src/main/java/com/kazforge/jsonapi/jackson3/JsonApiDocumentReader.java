package com.kazforge.jsonapi.jackson3;

import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.core.validation.JsonApiDocumentValidator;
import com.kazforge.jsonapi.core.validation.JsonApiValidationException;
import com.kazforge.jsonapi.jackson.diagnostic.CodecFailureCategory;
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiDocumentReadException;
import com.kazforge.jsonapi.jackson.diagnostic.SourceLocation;
import com.kazforge.jsonapi.jackson.document.DocumentReadContext;
import com.kazforge.jsonapi.jackson.internal.wire.ReadLocationIndex;
import com.kazforge.jsonapi.jackson3.internal.JsonApiWireReader;
import com.kazforge.jsonapi.jackson3.internal.ReadLocations;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads JSON:API JSON into a validated {@link JsonApiDocument} using an explicit {@link
 * DocumentReadContext}.
 *
 * <p>Decoding is token-driven through public core constructors, then aggregate validation runs
 * before any document is returned. Convenience overloads close only parsers they create;
 * caller-owned {@link InputStream} and {@link JsonParser} instances remain open.
 */
public final class JsonApiDocumentReader {

  private final JsonMapper mapper;
  private final DocumentReadContext context;
  private final JsonApiDocumentValidator validator = new JsonApiDocumentValidator();

  JsonApiDocumentReader(JsonMapper mapper, DocumentReadContext context) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.context = Objects.requireNonNull(context, "context");
  }

  /** Read context bound to this reader. */
  public DocumentReadContext context() {
    return context;
  }

  /** Configured mapper supplied to this reader for token-driven parsing. */
  JsonMapper mapper() {
    return mapper;
  }

  public JsonApiDocument readValue(String json) {
    Objects.requireNonNull(json, "json");
    try (JsonParser parser = mapper.createParser(json)) {
      return readAndValidate(parser, true);
    } catch (JacksonException ex) {
      throw wrapJackson(ex);
    }
  }

  public JsonApiDocument readValue(byte[] utf8Json) {
    Objects.requireNonNull(utf8Json, "utf8Json");
    try (JsonParser parser = mapper.createParser(utf8Json)) {
      return readAndValidate(parser, true);
    } catch (JacksonException ex) {
      throw wrapJackson(ex);
    }
  }

  /**
   * Reads from a caller-owned UTF-8 stream. The stream is not closed; only the parser created for
   * this call is closed.
   */
  public JsonApiDocument readValue(InputStream utf8Stream) {
    Objects.requireNonNull(utf8Stream, "utf8Stream");
    try (JsonParser parser = mapper.createParser(nonClosing(utf8Stream))) {
      return readAndValidate(parser, true);
    } catch (JacksonException ex) {
      throw wrapJackson(ex);
    }
  }

  /**
   * Reads one JSON:API document from a caller-owned parser starting at the current token (or the
   * next token if none is current). Does not require end-of-input after the document, so callers
   * may sequence multiple root values. The parser is not closed.
   */
  public JsonApiDocument readValue(JsonParser parser) {
    Objects.requireNonNull(parser, "parser");
    try {
      return readAndValidate(parser, false);
    } catch (JacksonException ex) {
      throw wrapJackson(ex);
    }
  }

  private JsonApiDocument readAndValidate(JsonParser parser, boolean requireEof) {
    ReadLocationIndex locations = new ReadLocationIndex();
    JsonApiDocument document;
    try {
      ensureCurrentToken(parser);
      document = JsonApiWireReader.readDocument(parser, context.primaryDataKind(), locations);
    } catch (JsonApiValidationException ex) {
      throw wrapValidation(CodecFailureCategory.LOCAL_VALIDATION, ex, locations, parser);
    } catch (JacksonException ex) {
      throw wrapJackson(ex, parser);
    }
    try {
      validator.validate(document, context.validationContext());
    } catch (JsonApiValidationException ex) {
      throw wrapValidation(CodecFailureCategory.AGGREGATE_VALIDATION, ex, locations, parser);
    }
    if (requireEof) {
      try {
        requireEndOfInput(parser);
      } catch (JacksonException ex) {
        throw wrapJackson(ex, parser);
      }
    }
    return document;
  }

  private static void ensureCurrentToken(JsonParser parser) {
    JsonToken token = parser.currentToken();
    // After a prior root value, the parser rests on that value's last token.
    if (token != JsonToken.START_OBJECT && token != JsonToken.START_ARRAY) {
      token = parser.nextToken();
      if (token == null) {
        throw new JsonApiDocumentReadException(
            CodecFailureCategory.MALFORMED_JSON,
            "",
            ReadLocations.current(parser),
            "Expected a JSON:API document object");
      }
    }
  }

  private static void requireEndOfInput(JsonParser parser) {
    JsonToken trailing = parser.nextToken();
    if (trailing != null) {
      throw new JsonApiDocumentReadException(
          CodecFailureCategory.UNEXPECTED_TOKEN,
          "",
          ReadLocations.token(parser),
          "Trailing content after JSON:API document");
    }
  }

  private static JsonApiDocumentReadException wrapValidation(
      CodecFailureCategory category,
      JsonApiValidationException ex,
      ReadLocationIndex locations,
      JsonParser parser) {
    SourceLocation location = locations.resolve(ex.jsonPointer());
    if (!location.isKnown() && category == CodecFailureCategory.LOCAL_VALIDATION) {
      location = ReadLocations.current(parser);
    }
    String message =
        category == CodecFailureCategory.AGGREGATE_VALIDATION
            ? "Aggregate validation failed"
            : "Local validation failed";
    // Do not attach the raw cause: core/Jackson messages may contain payload-derived values.
    return new JsonApiDocumentReadException(
        category, ex.jsonPointer(), location, ex.ruleCode(), message);
  }

  private static JsonApiDocumentReadException wrapJackson(JacksonException ex) {
    return new JsonApiDocumentReadException(
        CodecFailureCategory.MALFORMED_JSON,
        "",
        ReadLocations.from(ex.getLocation()),
        "Malformed JSON");
  }

  private static JsonApiDocumentReadException wrapJackson(JacksonException ex, JsonParser parser) {
    return new JsonApiDocumentReadException(
        CodecFailureCategory.MALFORMED_JSON,
        "",
        ReadLocations.fromOrCurrent(ex.getLocation(), parser),
        "Malformed JSON");
  }

  private static InputStream nonClosing(InputStream delegate) {
    return new FilterInputStream(delegate) {
      @Override
      public void close() {
        // Caller owns the underlying stream.
      }
    };
  }
}
