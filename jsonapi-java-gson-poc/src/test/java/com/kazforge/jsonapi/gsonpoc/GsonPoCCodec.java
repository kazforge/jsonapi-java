package com.kazforge.jsonapi.gsonpoc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.kazforge.jsonapi.core.model.Attributes;
import com.kazforge.jsonapi.core.model.DocumentData;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.Relationships;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.model.ResourceObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spike-only Gson tree codec for the representative KAZ-137 contract slice.
 *
 * <p>This deliberately supports only single-resource documents, included resources, attributes,
 * and relationship linkage. It demonstrates backend independence, not JSON:API conformance.
 */
final class GsonPoCCodec {

  private final Gson gson;

  GsonPoCCodec(Gson gson) {
    this.gson = gson;
  }

  String write(JsonApiDocument document) {
    JsonObject root = new JsonObject();
    if (!(document.data() instanceof DocumentData.SingleResource single)) {
      throw new IllegalArgumentException("PoC codec supports single-resource data only");
    }
    root.add("data", writeResource(single.resource()));
    if (document.included() != null) {
      JsonArray included = new JsonArray();
      for (ResourceObject resource : document.included()) {
        included.add(writeResource(resource));
      }
      root.add("included", included);
    }
    return gson.toJson(root);
  }

  JsonApiDocument read(String json) {
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    ResourceObject primary = readResource(root.getAsJsonObject("data"));
    List<ResourceObject> included = null;
    if (root.has("included")) {
      included = new ArrayList<>();
      for (JsonElement element : root.getAsJsonArray("included")) {
        included.add(readResource(element.getAsJsonObject()));
      }
      included = List.copyOf(included);
    }
    return new JsonApiDocument(
        new DocumentData.SingleResource(primary),
        null,
        null,
        null,
        null,
        included,
        Map.of());
  }

  private JsonObject writeResource(ResourceObject resource) {
    JsonObject object = new JsonObject();
    object.addProperty("type", resource.type());
    if (resource.id() != null) {
      object.addProperty("id", resource.id());
    }
    if (resource.lid() != null) {
      object.addProperty("lid", resource.lid());
    }

    if (resource.attributes() != null) {
      JsonObject attributes = new JsonObject();
      for (Map.Entry<String, ?> entry : resource.attributes().attributes().entrySet()) {
        attributes.add(entry.getKey(), gson.toJsonTree(entry.getValue()));
      }
      object.add("attributes", attributes);
    }

    if (resource.relationships() != null) {
      JsonObject relationships = new JsonObject();
      for (Map.Entry<String, Relationship> entry :
          resource.relationships().relationships().entrySet()) {
        JsonObject relationship = new JsonObject();
        relationship.add("data", writeRelationshipData(entry.getValue().data()));
        relationships.add(entry.getKey(), relationship);
      }
      object.add("relationships", relationships);
    }
    return object;
  }

  private JsonElement writeRelationshipData(RelationshipData data) {
    if (data instanceof RelationshipData.NullLinkage) {
      return JsonNull.INSTANCE;
    }
    if (data instanceof RelationshipData.SingleLinkage single) {
      return writeIdentifier(single.identifier());
    }
    if (data instanceof RelationshipData.IdentifierCollectionLinkage collection) {
      JsonArray array = new JsonArray();
      for (ResourceIdentifier identifier : collection.identifiers()) {
        array.add(writeIdentifier(identifier));
      }
      return array;
    }
    throw new IllegalArgumentException("Unsupported relationship data " + data);
  }

  private static JsonObject writeIdentifier(ResourceIdentifier identifier) {
    JsonObject object = new JsonObject();
    object.addProperty("type", identifier.type());
    if (identifier.id() != null) {
      object.addProperty("id", identifier.id());
    }
    if (identifier.lid() != null) {
      object.addProperty("lid", identifier.lid());
    }
    return object;
  }

  private ResourceObject readResource(JsonObject object) {
    String type = object.get("type").getAsString();
    String id = stringOrNull(object, "id");
    String lid = stringOrNull(object, "lid");

    Attributes attributes = null;
    if (object.has("attributes")) {
      Map<String, Object> values = new LinkedHashMap<>();
      for (Map.Entry<String, JsonElement> entry :
          object.getAsJsonObject("attributes").entrySet()) {
        values.put(entry.getKey(), openValue(entry.getValue()));
      }
      attributes = Attributes.ofAttributes(values);
    }

    Relationships relationships = null;
    if (object.has("relationships")) {
      Map<String, Relationship> values = new LinkedHashMap<>();
      for (Map.Entry<String, JsonElement> entry :
          object.getAsJsonObject("relationships").entrySet()) {
        JsonObject relationshipObject = entry.getValue().getAsJsonObject();
        values.put(
            entry.getKey(),
            Relationship.withData(readRelationshipData(relationshipObject.get("data"))));
      }
      relationships = Relationships.ofRelationships(values);
    }

    return new ResourceObject(
        type, id, lid, attributes, relationships, null, null, Map.of());
  }

  private RelationshipData readRelationshipData(JsonElement element) {
    if (element == null || element.isJsonNull()) {
      return RelationshipData.NullLinkage.INSTANCE;
    }
    if (element.isJsonArray()) {
      List<ResourceIdentifier> identifiers = new ArrayList<>();
      for (JsonElement item : element.getAsJsonArray()) {
        identifiers.add(readIdentifier(item.getAsJsonObject()));
      }
      return new RelationshipData.IdentifierCollectionLinkage(identifiers);
    }
    return new RelationshipData.SingleLinkage(readIdentifier(element.getAsJsonObject()));
  }

  private static ResourceIdentifier readIdentifier(JsonObject object) {
    return new ResourceIdentifier(
        object.get("type").getAsString(),
        stringOrNull(object, "id"),
        stringOrNull(object, "lid"),
        null,
        Map.of());
  }

  private static String stringOrNull(JsonObject object, String name) {
    return object.has(name) && !object.get(name).isJsonNull()
        ? object.get(name).getAsString()
        : null;
  }

  private static Object openValue(JsonElement element) {
    if (element == null || element.isJsonNull()) {
      return null;
    }
    if (element.isJsonPrimitive()) {
      JsonPrimitive primitive = element.getAsJsonPrimitive();
      if (primitive.isBoolean()) {
        return primitive.getAsBoolean();
      }
      if (primitive.isNumber()) {
        return primitive.getAsNumber();
      }
      return primitive.getAsString();
    }
    if (element.isJsonArray()) {
      List<Object> values = new ArrayList<>();
      for (JsonElement item : element.getAsJsonArray()) {
        values.add(openValue(item));
      }
      return java.util.Collections.unmodifiableList(values);
    }
    Map<String, Object> values = new LinkedHashMap<>();
    for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
      values.put(entry.getKey(), openValue(entry.getValue()));
    }
    return java.util.Collections.unmodifiableMap(values);
  }
}
