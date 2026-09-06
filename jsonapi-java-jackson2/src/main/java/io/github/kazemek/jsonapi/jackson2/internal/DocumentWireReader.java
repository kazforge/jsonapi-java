package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import io.github.kazemek.jsonapi.core.model.DocumentData;
import io.github.kazemek.jsonapi.core.model.ErrorObject;
import io.github.kazemek.jsonapi.core.model.JsonApiDocument;
import io.github.kazemek.jsonapi.core.model.JsonApiMembers;
import io.github.kazemek.jsonapi.core.model.JsonApiObject;
import io.github.kazemek.jsonapi.core.model.Links;
import io.github.kazemek.jsonapi.core.model.Meta;
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier;
import io.github.kazemek.jsonapi.core.model.ResourceObject;
import io.github.kazemek.jsonapi.jackson.diagnostic.CodecFailureCategory;
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiDocumentReadException;
import io.github.kazemek.jsonapi.jackson.document.PrimaryDataKind;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Document-root, primary-data, jsonapi, and meta decoding. */
final class DocumentWireReader {

  private static final String PATH_DATA = "/data";

  private static final Set<String> DOCUMENT_MEMBERS =
      Set.of(
          JsonApiMembers.DATA,
          JsonApiMembers.ERRORS,
          JsonApiMembers.META,
          JsonApiMembers.JSONAPI,
          JsonApiMembers.LINKS,
          JsonApiMembers.INCLUDED);

  private static final Set<String> JSONAPI_MEMBERS =
      Set.of(
          JsonApiMembers.VERSION, JsonApiMembers.EXT, JsonApiMembers.PROFILE, JsonApiMembers.META);

  private DocumentWireReader() {}

  static JsonApiDocument readDocument(
      JsonParser parser, PrimaryDataKind primaryDataKind, ReadLocationIndex locations)
      throws IOException {
    JsonPointerAccumulator pointer = new JsonPointerAccumulator(locations);
    try {
      DocumentDraft draft = new DocumentDraft(primaryDataKind);
      WireObjectMembers.forEachMember(
          parser, pointer, name -> draft.readMember(name, parser, pointer));
      return draft.build(pointer);
    } catch (JsonProcessingException ex) {
      // Do not attach the raw cause: Jackson messages may include source details.
      throw new JsonApiDocumentReadException(
          CodecFailureCategory.MALFORMED_JSON,
          pointer.path(),
          ReadLocations.from(ex.getLocation()),
          "Malformed JSON");
    }
  }

  static DocumentData readDocumentData(
      JsonParser parser, PrimaryDataKind kind, JsonPointerAccumulator pointer) throws IOException {
    JsonToken token = parser.currentToken();
    if (token == JsonToken.VALUE_NULL) {
      return DocumentData.NullData.INSTANCE;
    }
    if (token == JsonToken.START_OBJECT) {
      return switch (kind) {
        case RESOURCE -> {
          ResourceObject resource = ResourceWireReader.readResourceObject(parser, pointer);
          yield ValidationPointers.construct(
              pointer, PATH_DATA, () -> new DocumentData.SingleResource(resource));
        }
        case RESOURCE_IDENTIFIER -> {
          ResourceIdentifier identifier =
              ResourceWireReader.readResourceIdentifier(parser, pointer);
          yield ValidationPointers.construct(
              pointer, PATH_DATA, () -> new DocumentData.SingleIdentifier(identifier));
        }
      };
    }
    if (token == JsonToken.START_ARRAY) {
      return switch (kind) {
        case RESOURCE -> {
          List<ResourceObject> resources = ResourceWireReader.readResourceObjects(parser, pointer);
          yield ValidationPointers.construct(
              pointer, PATH_DATA, () -> new DocumentData.ResourceCollection(resources));
        }
        case RESOURCE_IDENTIFIER -> {
          List<ResourceIdentifier> identifiers =
              ResourceWireReader.readResourceIdentifiers(parser, pointer);
          yield ValidationPointers.construct(
              pointer, PATH_DATA, () -> new DocumentData.IdentifierCollection(identifiers));
        }
      };
    }
    throw WireTokens.unexpectedToken(
        token, "null, object, or array for document data", pointer, parser);
  }

  static Meta readMeta(JsonParser parser, JsonPointerAccumulator pointer) throws IOException {
    Map<String, @Nullable Object> members = WireTokens.newNullableMap();
    WireObjectMembers.forEachMember(
        parser,
        pointer,
        name -> WireTokens.putOpen(members, name, WireTokens.readOpenValue(parser, pointer)));
    return ValidationPointers.construct(
        pointer, "/meta", () -> Meta.of(ValidationPointers.forCore(members)));
  }

  static JsonApiObject readJsonApiObject(JsonParser parser, JsonPointerAccumulator pointer)
      throws IOException {
    JsonApiObjectDraft draft = new JsonApiObjectDraft();
    WireObjectMembers.forEachMember(
        parser, pointer, name -> draft.readMember(name, parser, pointer));
    return draft.build(pointer);
  }

  private static final class DocumentDraft {
    private final PrimaryDataKind primaryDataKind;
    private boolean dataPresent;
    private @Nullable DocumentData data;
    private @Nullable List<ErrorObject> errors;
    private @Nullable Meta meta;
    private @Nullable JsonApiObject jsonapi;
    private @Nullable Links links;
    private @Nullable List<ResourceObject> included;
    private final Map<String, @Nullable Object> additional = WireTokens.newNullableMap();

    DocumentDraft(PrimaryDataKind primaryDataKind) {
      this.primaryDataKind = primaryDataKind;
    }

    void readMember(String name, JsonParser parser, JsonPointerAccumulator pointer)
        throws IOException {
      switch (name) {
        case JsonApiMembers.DATA -> {
          dataPresent = true;
          data = readDocumentData(parser, primaryDataKind, pointer);
        }
        case JsonApiMembers.ERRORS -> errors = ErrorWireReader.readErrorObjects(parser, pointer);
        case JsonApiMembers.META -> meta = readMeta(parser, pointer);
        case JsonApiMembers.JSONAPI -> jsonapi = readJsonApiObject(parser, pointer);
        case JsonApiMembers.LINKS -> links = LinkWireReader.readLinks(parser, pointer);
        case JsonApiMembers.INCLUDED ->
            included = ResourceWireReader.readResourceObjects(parser, pointer);
        default -> {
          if (DOCUMENT_MEMBERS.contains(name)) {
            throw WireTokens.unexpected(
                "Unexpected document member handling for: " + name, pointer, parser);
          }
          WireTokens.putOpen(additional, name, WireTokens.readOpenValue(parser, pointer));
        }
      }
    }

    JsonApiDocument build(JsonPointerAccumulator pointer) {
      DocumentData documentData = dataPresent ? data : null;
      List<ErrorObject> documentErrors = errors;
      Meta documentMeta = meta;
      JsonApiObject documentJsonapi = jsonapi;
      Links documentLinks = links;
      List<ResourceObject> documentIncluded = included;
      return ValidationPointers.construct(
          pointer,
          "",
          () ->
              new JsonApiDocument(
                  documentData,
                  documentErrors,
                  documentMeta,
                  documentJsonapi,
                  documentLinks,
                  documentIncluded,
                  ValidationPointers.forCore(additional)));
    }
  }

  private static final class JsonApiObjectDraft {
    private @Nullable String version;
    private @Nullable List<String> ext;
    private @Nullable List<String> profile;
    private @Nullable Meta meta;
    private final Map<String, @Nullable Object> additional = WireTokens.newNullableMap();

    void readMember(String name, JsonParser parser, JsonPointerAccumulator pointer)
        throws IOException {
      switch (name) {
        case JsonApiMembers.VERSION -> version = WireTokens.readRequiredString(parser, pointer);
        case JsonApiMembers.EXT -> ext = WireTokens.readStringArray(parser, pointer);
        case JsonApiMembers.PROFILE -> profile = WireTokens.readStringArray(parser, pointer);
        case JsonApiMembers.META -> meta = readMeta(parser, pointer);
        default -> {
          if (JSONAPI_MEMBERS.contains(name)) {
            throw WireTokens.unexpected(
                "Unexpected jsonapi member handling for: " + name, pointer, parser);
          }
          WireTokens.putOpen(additional, name, WireTokens.readOpenValue(parser, pointer));
        }
      }
    }

    JsonApiObject build(JsonPointerAccumulator pointer) {
      String jsonApiVersion = version;
      List<String> jsonApiExt = ext;
      List<String> jsonApiProfile = profile;
      Meta jsonApiMeta = meta;
      return ValidationPointers.construct(
          pointer,
          "/jsonapi",
          () ->
              new JsonApiObject(
                  jsonApiVersion,
                  jsonApiExt,
                  jsonApiProfile,
                  jsonApiMeta,
                  ValidationPointers.forCore(additional)));
    }
  }
}
