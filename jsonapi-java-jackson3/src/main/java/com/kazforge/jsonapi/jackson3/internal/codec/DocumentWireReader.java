package com.kazforge.jsonapi.jackson3.internal.codec;

import com.kazforge.jsonapi.core.aggregate.ValidationContext;
import com.kazforge.jsonapi.core.model.DocumentData;
import com.kazforge.jsonapi.core.model.ErrorObject;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.core.model.JsonApiMembers;
import com.kazforge.jsonapi.core.model.JsonApiObject;
import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.core.validation.LinksContext;
import com.kazforge.jsonapi.jackson.diagnostic.CodecFailureCategory;
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiDocumentReadException;
import com.kazforge.jsonapi.jackson.document.PrimaryDataKind;
import com.kazforge.jsonapi.jackson.internal.wire.JsonPointerAccumulator;
import com.kazforge.jsonapi.jackson.internal.wire.MemberClassifier;
import com.kazforge.jsonapi.jackson.internal.wire.ReadLocationIndex;
import com.kazforge.jsonapi.jackson.internal.wire.ValidationPointers;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

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
      JsonParser parser,
      PrimaryDataKind primaryDataKind,
      ValidationContext validationContext,
      ReadLocationIndex locations) {
    JsonPointerAccumulator pointer = new JsonPointerAccumulator(locations);
    try {
      DocumentDraft draft = new DocumentDraft(primaryDataKind, validationContext);
      WireObjectMembers.forEachMember(
          parser,
          pointer,
          name ->
              DOCUMENT_MEMBERS.contains(name)
                  || MemberClassifier.isRetainedStructuralMember(name, validationContext),
          name -> draft.readMember(name, parser, pointer));
      return draft.build(pointer);
    } catch (JacksonException ex) {
      // Do not attach the raw cause: Jackson messages may include source details.
      throw new JsonApiDocumentReadException(
          CodecFailureCategory.MALFORMED_JSON,
          pointer.path(),
          ReadLocations.fromOrCurrent(ex.getLocation(), parser),
          "Malformed JSON");
    }
  }

  static DocumentData readDocumentData(
      JsonParser parser,
      PrimaryDataKind kind,
      ValidationContext validationContext,
      JsonPointerAccumulator pointer) {
    JsonToken token = parser.currentToken();
    if (token == JsonToken.VALUE_NULL) {
      return DocumentData.NullData.INSTANCE;
    }
    if (token == JsonToken.START_OBJECT) {
      return switch (kind) {
        case RESOURCE -> {
          ResourceObject resource =
              ResourceWireReader.readResourceObject(parser, validationContext, pointer);
          yield ValidationPointers.construct(
              pointer.path(), PATH_DATA, () -> new DocumentData.SingleResource(resource));
        }
        case RESOURCE_IDENTIFIER -> {
          ResourceIdentifier identifier =
              ResourceWireReader.readResourceIdentifier(parser, validationContext, pointer);
          yield ValidationPointers.construct(
              pointer.path(), PATH_DATA, () -> new DocumentData.SingleIdentifier(identifier));
        }
      };
    }
    if (token == JsonToken.START_ARRAY) {
      return switch (kind) {
        case RESOURCE -> {
          List<ResourceObject> resources =
              ResourceWireReader.readResourceObjects(parser, validationContext, pointer);
          yield ValidationPointers.construct(
              pointer.path(), PATH_DATA, () -> new DocumentData.ResourceCollection(resources));
        }
        case RESOURCE_IDENTIFIER -> {
          List<ResourceIdentifier> identifiers =
              ResourceWireReader.readResourceIdentifiers(parser, validationContext, pointer);
          yield ValidationPointers.construct(
              pointer.path(), PATH_DATA, () -> new DocumentData.IdentifierCollection(identifiers));
        }
      };
    }
    throw WireTokens.unexpectedToken(
        token, "null, object, or array for document data", pointer, parser);
  }

  static JsonApiObject readJsonApiObject(
      JsonParser parser, ValidationContext validationContext, JsonPointerAccumulator pointer) {
    JsonApiObjectDraft draft = new JsonApiObjectDraft(validationContext);
    WireObjectMembers.forEachMember(
        parser,
        pointer,
        name ->
            JSONAPI_MEMBERS.contains(name)
                || MemberClassifier.isRetainedStructuralMember(name, validationContext),
        name -> draft.readMember(name, parser, pointer));
    return draft.build(pointer);
  }

  private static final class DocumentDraft {
    private final PrimaryDataKind primaryDataKind;
    private final ValidationContext validationContext;
    private boolean dataPresent;
    private @Nullable DocumentData data;
    private @Nullable List<ErrorObject> errors;
    private @Nullable Meta meta;
    private @Nullable JsonApiObject jsonapi;
    private @Nullable Links links;
    private @Nullable List<ResourceObject> included;
    private final Map<String, @Nullable Object> additional = WireTokens.newNullableMap();

    DocumentDraft(PrimaryDataKind primaryDataKind, ValidationContext validationContext) {
      this.primaryDataKind = primaryDataKind;
      this.validationContext = validationContext;
    }

    void readMember(String name, JsonParser parser, JsonPointerAccumulator pointer) {
      switch (name) {
        case JsonApiMembers.DATA -> {
          dataPresent = true;
          data = readDocumentData(parser, primaryDataKind, validationContext, pointer);
        }
        case JsonApiMembers.ERRORS ->
            errors = ErrorWireReader.readErrorObjects(parser, validationContext, pointer);
        case JsonApiMembers.META -> meta = WireMetaReader.readMeta(parser, pointer);
        case JsonApiMembers.JSONAPI ->
            jsonapi = readJsonApiObject(parser, validationContext, pointer);
        case JsonApiMembers.LINKS ->
            links =
                LinkWireReader.readLinks(
                    parser, validationContext, LinksContext.TOP_LEVEL, pointer);
        case JsonApiMembers.INCLUDED ->
            included = ResourceWireReader.readResourceObjects(parser, validationContext, pointer);
        default -> {
          if (DOCUMENT_MEMBERS.contains(name)) {
            throw WireTokens.unexpected(
                "Unexpected document member handling for: " + name, pointer, parser);
          }
          if (MemberClassifier.isRetainedStructuralMember(name, validationContext)) {
            WireTokens.putOpen(additional, name, WireOpenValues.readOpenValue(parser, pointer));
          } else {
            WireTokens.skipValue(parser);
          }
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
          pointer.path(),
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
    private final ValidationContext validationContext;
    private @Nullable String version;
    private @Nullable List<String> ext;
    private @Nullable List<String> profile;
    private @Nullable Meta meta;
    private final Map<String, @Nullable Object> additional = WireTokens.newNullableMap();

    JsonApiObjectDraft(ValidationContext validationContext) {
      this.validationContext = validationContext;
    }

    void readMember(String name, JsonParser parser, JsonPointerAccumulator pointer) {
      switch (name) {
        case JsonApiMembers.VERSION -> version = WireTokens.readRequiredString(parser, pointer);
        case JsonApiMembers.EXT -> ext = WireTokens.readStringArray(parser, pointer);
        case JsonApiMembers.PROFILE -> profile = WireTokens.readStringArray(parser, pointer);
        case JsonApiMembers.META -> meta = WireMetaReader.readMeta(parser, pointer);
        default -> {
          if (JSONAPI_MEMBERS.contains(name)) {
            throw WireTokens.unexpected(
                "Unexpected jsonapi member handling for: " + name, pointer, parser);
          }
          if (MemberClassifier.isRetainedStructuralMember(name, validationContext)) {
            WireTokens.putOpen(additional, name, WireOpenValues.readOpenValue(parser, pointer));
          } else {
            WireTokens.skipValue(parser);
          }
        }
      }
    }

    JsonApiObject build(JsonPointerAccumulator pointer) {
      String jsonApiVersion = version;
      List<String> jsonApiExt = ext;
      List<String> jsonApiProfile = profile;
      Meta jsonApiMeta = meta;
      return ValidationPointers.construct(
          pointer.path(),
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
