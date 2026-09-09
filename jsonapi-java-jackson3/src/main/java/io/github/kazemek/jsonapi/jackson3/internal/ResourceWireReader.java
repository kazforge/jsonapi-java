package io.github.kazemek.jsonapi.jackson3.internal;

import io.github.kazemek.jsonapi.core.model.Attributes;
import io.github.kazemek.jsonapi.core.model.JsonApiMembers;
import io.github.kazemek.jsonapi.core.model.Links;
import io.github.kazemek.jsonapi.core.model.Meta;
import io.github.kazemek.jsonapi.core.model.Relationship;
import io.github.kazemek.jsonapi.core.model.RelationshipData;
import io.github.kazemek.jsonapi.core.model.Relationships;
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier;
import io.github.kazemek.jsonapi.core.model.ResourceObject;
import io.github.kazemek.jsonapi.core.validation.JsonApiValidationException;
import io.github.kazemek.jsonapi.core.validation.ValidationRuleCode;
import io.github.kazemek.jsonapi.jackson.internal.wire.JsonPointerAccumulator;
import io.github.kazemek.jsonapi.jackson.internal.wire.MemberClassifier;
import io.github.kazemek.jsonapi.jackson.internal.wire.ValidationPointers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

/** Resource object, identifier, attributes, and relationship decoding. */
final class ResourceWireReader {

  private static final Set<String> RESOURCE_MEMBERS =
      Set.of(
          JsonApiMembers.TYPE,
          JsonApiMembers.ID,
          JsonApiMembers.LID,
          JsonApiMembers.ATTRIBUTES,
          JsonApiMembers.RELATIONSHIPS,
          JsonApiMembers.LINKS,
          JsonApiMembers.META);

  private static final Set<String> IDENTIFIER_MEMBERS =
      Set.of(JsonApiMembers.TYPE, JsonApiMembers.ID, JsonApiMembers.LID, JsonApiMembers.META);

  private static final Set<String> RELATIONSHIP_MEMBERS =
      Set.of(JsonApiMembers.DATA, JsonApiMembers.LINKS, JsonApiMembers.META);

  private ResourceWireReader() {}

  static List<ResourceObject> readResourceObjects(
      JsonParser parser, JsonPointerAccumulator pointer) {
    WireTokens.expectToken(parser, JsonToken.START_ARRAY, pointer);
    List<ResourceObject> resources = new ArrayList<>();
    int index = 0;
    while (parser.nextToken() != JsonToken.END_ARRAY) {
      pointer.pushIndex(index);
      pointer.capture(ReadLocations.token(parser));
      resources.add(readResourceObject(parser, pointer));
      pointer.pop();
      index++;
    }
    return List.copyOf(resources);
  }

  static ResourceObject readResourceObject(JsonParser parser, JsonPointerAccumulator pointer) {
    ResourceDraft draft = new ResourceDraft();
    WireObjectMembers.forEachMember(
        parser, pointer, name -> draft.readMember(name, parser, pointer));
    return draft.build(pointer);
  }

  static List<ResourceIdentifier> readResourceIdentifiers(
      JsonParser parser, JsonPointerAccumulator pointer) {
    WireTokens.expectToken(parser, JsonToken.START_ARRAY, pointer);
    List<ResourceIdentifier> identifiers = new ArrayList<>();
    int index = 0;
    while (parser.nextToken() != JsonToken.END_ARRAY) {
      pointer.pushIndex(index);
      pointer.capture(ReadLocations.token(parser));
      identifiers.add(readResourceIdentifier(parser, pointer));
      pointer.pop();
      index++;
    }
    return List.copyOf(identifiers);
  }

  static ResourceIdentifier readResourceIdentifier(
      JsonParser parser, JsonPointerAccumulator pointer) {
    IdentifierDraft draft = new IdentifierDraft();
    WireObjectMembers.forEachMember(
        parser, pointer, name -> draft.readMember(name, parser, pointer));
    return draft.build(pointer);
  }

  static Attributes readAttributes(JsonParser parser, JsonPointerAccumulator pointer) {
    Map<String, @Nullable Object> attributes = WireTokens.newNullableMap();
    Map<String, @Nullable Object> additional = WireTokens.newNullableMap();
    WireObjectMembers.forEachMember(
        parser,
        pointer,
        name -> {
          Object value = WireTokens.readOpenValue(parser, pointer);
          if (MemberClassifier.isPassThroughAttributeOrRelationship(name)) {
            WireTokens.putOpen(additional, name, value);
          } else {
            WireTokens.putOpen(attributes, name, value);
          }
        });
    return ValidationPointers.construct(
        pointer.path(),
        "/attributes",
        () ->
            Attributes.of(
                ValidationPointers.forCore(attributes), ValidationPointers.forCore(additional)));
  }

  static Relationships readRelationships(JsonParser parser, JsonPointerAccumulator pointer) {
    Map<String, @Nullable Relationship> relationships = WireTokens.newNullableRelationshipMap();
    Map<String, @Nullable Object> additional = WireTokens.newNullableMap();
    WireObjectMembers.forEachMember(
        parser,
        pointer,
        name -> {
          if (MemberClassifier.isPassThroughAttributeOrRelationship(name)) {
            WireTokens.putOpen(additional, name, WireTokens.readOpenValue(parser, pointer));
          } else {
            WireTokens.putRelationship(relationships, name, readRelationship(parser, pointer));
          }
        });
    return ValidationPointers.construct(
        pointer.path(),
        "/relationships",
        () -> Relationships.of(relationships, ValidationPointers.forCore(additional)));
  }

  static Relationship readRelationship(JsonParser parser, JsonPointerAccumulator pointer) {
    RelationshipDraft draft = new RelationshipDraft();
    WireObjectMembers.forEachMember(
        parser, pointer, name -> draft.readMember(name, parser, pointer));
    return draft.build(pointer);
  }

  static RelationshipData readRelationshipData(JsonParser parser, JsonPointerAccumulator pointer) {
    JsonToken token = parser.currentToken();
    if (token == JsonToken.VALUE_NULL) {
      return RelationshipData.NullLinkage.INSTANCE;
    }
    if (token == JsonToken.START_OBJECT) {
      ResourceIdentifier identifier = readResourceIdentifier(parser, pointer);
      return ValidationPointers.construct(
          pointer.path(),
          "/relationships/data",
          () -> new RelationshipData.SingleLinkage(identifier));
    }
    if (token == JsonToken.START_ARRAY) {
      List<ResourceIdentifier> identifiers = readResourceIdentifiers(parser, pointer);
      return ValidationPointers.construct(
          pointer.path(),
          "/relationships/data",
          () -> new RelationshipData.IdentifierCollectionLinkage(identifiers));
    }
    throw WireTokens.unexpectedToken(
        token, "null, object, or array for relationship data", pointer, parser);
  }

  private static final class ResourceDraft {
    private @Nullable String type;
    private @Nullable String id;
    private @Nullable String lid;
    private @Nullable Attributes attributes;
    private @Nullable Relationships relationships;
    private @Nullable Links links;
    private @Nullable Meta meta;
    private final Map<String, @Nullable Object> additional = WireTokens.newNullableMap();

    void readMember(String name, JsonParser parser, JsonPointerAccumulator pointer) {
      switch (name) {
        case JsonApiMembers.TYPE -> type = WireTokens.readRequiredString(parser, pointer);
        case JsonApiMembers.ID -> id = WireTokens.readRequiredString(parser, pointer);
        case JsonApiMembers.LID -> lid = WireTokens.readRequiredString(parser, pointer);
        case JsonApiMembers.ATTRIBUTES -> attributes = readAttributes(parser, pointer);
        case JsonApiMembers.RELATIONSHIPS -> relationships = readRelationships(parser, pointer);
        case JsonApiMembers.LINKS -> links = LinkWireReader.readLinks(parser, pointer);
        case JsonApiMembers.META -> meta = DocumentWireReader.readMeta(parser, pointer);
        default -> {
          if (RESOURCE_MEMBERS.contains(name)) {
            throw WireTokens.unexpected(
                "Unexpected resource member handling for: " + name, pointer, parser);
          }
          WireTokens.putOpen(additional, name, WireTokens.readOpenValue(parser, pointer));
        }
      }
    }

    ResourceObject build(JsonPointerAccumulator pointer) {
      if (type == null) {
        throw new JsonApiValidationException(
            ValidationRuleCode.MISSING_RESOURCE_TYPE,
            pointer.path() + "/type",
            "Resource object requires type");
      }
      String resourceType = type;
      String resourceId = id;
      String resourceLid = lid;
      Attributes resourceAttributes = attributes;
      Relationships resourceRelationships = relationships;
      Links resourceLinks = links;
      Meta resourceMeta = meta;
      return ValidationPointers.construct(
          pointer.path(),
          "/data",
          () ->
              new ResourceObject(
                  resourceType,
                  resourceId,
                  resourceLid,
                  resourceAttributes,
                  resourceRelationships,
                  resourceLinks,
                  resourceMeta,
                  ValidationPointers.forCore(additional)));
    }
  }

  private static final class IdentifierDraft {
    private @Nullable String type;
    private @Nullable String id;
    private @Nullable String lid;
    private @Nullable Meta meta;
    private final Map<String, @Nullable Object> additional = WireTokens.newNullableMap();

    void readMember(String name, JsonParser parser, JsonPointerAccumulator pointer) {
      switch (name) {
        case JsonApiMembers.TYPE -> type = WireTokens.readRequiredString(parser, pointer);
        case JsonApiMembers.ID -> id = WireTokens.readRequiredString(parser, pointer);
        case JsonApiMembers.LID -> lid = WireTokens.readRequiredString(parser, pointer);
        case JsonApiMembers.META -> meta = DocumentWireReader.readMeta(parser, pointer);
        default -> {
          if (IDENTIFIER_MEMBERS.contains(name)) {
            throw WireTokens.unexpected(
                "Unexpected resource identifier member handling for: " + name, pointer, parser);
          }
          WireTokens.putOpen(additional, name, WireTokens.readOpenValue(parser, pointer));
        }
      }
    }

    ResourceIdentifier build(JsonPointerAccumulator pointer) {
      if (type == null) {
        throw new JsonApiValidationException(
            ValidationRuleCode.MISSING_RESOURCE_TYPE,
            pointer.path() + "/type",
            "Resource identifier requires type");
      }
      String identifierType = type;
      String identifierId = id;
      String identifierLid = lid;
      Meta identifierMeta = meta;
      return ValidationPointers.construct(
          pointer.path(),
          "/data",
          () ->
              new ResourceIdentifier(
                  identifierType,
                  identifierId,
                  identifierLid,
                  identifierMeta,
                  ValidationPointers.forCore(additional)));
    }
  }

  private static final class RelationshipDraft {
    private boolean dataPresent;
    private @Nullable RelationshipData data;
    private @Nullable Links links;
    private @Nullable Meta meta;
    private final Map<String, @Nullable Object> additional = WireTokens.newNullableMap();

    void readMember(String name, JsonParser parser, JsonPointerAccumulator pointer) {
      switch (name) {
        case JsonApiMembers.DATA -> {
          dataPresent = true;
          data = readRelationshipData(parser, pointer);
        }
        case JsonApiMembers.LINKS -> links = LinkWireReader.readLinks(parser, pointer);
        case JsonApiMembers.META -> meta = DocumentWireReader.readMeta(parser, pointer);
        default -> {
          if (RELATIONSHIP_MEMBERS.contains(name)) {
            throw WireTokens.unexpected(
                "Unexpected relationship member handling for: " + name, pointer, parser);
          }
          WireTokens.putOpen(additional, name, WireTokens.readOpenValue(parser, pointer));
        }
      }
    }

    Relationship build(JsonPointerAccumulator pointer) {
      RelationshipData relationshipData = dataPresent ? data : null;
      Links relationshipLinks = links;
      Meta relationshipMeta = meta;
      return ValidationPointers.construct(
          pointer.path(),
          "/relationships",
          () ->
              new Relationship(
                  relationshipData,
                  relationshipLinks,
                  relationshipMeta,
                  ValidationPointers.forCore(additional)));
    }
  }
}
