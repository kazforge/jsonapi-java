package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.RelationshipData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Mapping-local test double for {@link ReadResourceBackend} over string property tokens. Test code
 * configures definitions and per-member native outcomes directly; no read semantics beyond that
 * configuration are simulated.
 *
 * <p>It carries passive observations of the native callbacks the shared reader invokes: wire
 * identifier parsing and relationship conversion. The observers record invocations and return
 * configured or deterministic results; they implement no Jackson behavior.
 */
@NullMarked
final class MappingFakeReadResourceBackend implements ReadResourceBackend<String> {

  final Map<String, ReadResourceDefinition<String>> definitions = new LinkedHashMap<>();
  final Map<String, Class<?>> propertyRawTypes = new LinkedHashMap<>();

  /** Declared relationship conversion results by relationship property token. */
  final Map<String, @Nullable Object> relationshipConversions = new LinkedHashMap<>();

  /** Relationship tokens whose configured conversion throws. */
  final List<String> failingRelationshipTokens = new ArrayList<>();

  /** Wire identifiers observed through {@link #parseIdentifier}, in call order. */
  final List<String> parsedIdentifiers = new ArrayList<>();

  /** One relationship-conversion invocation: property token and present linkage data. */
  record RelationshipObservation(String propertyToken, RelationshipData data) {}

  /** Relationship-conversion observations of the shared reader, in call order. */
  final List<RelationshipObservation> relationshipObservations = new ArrayList<>();

  boolean parseReturnsNull;
  boolean parseThrows;

  static ReadProperty<String> property(
      PropertyRole role, String logicalName, String externalName, String jsonapiName) {
    return property(role, logicalName, externalName, jsonapiName, true);
  }

  static ReadProperty<String> property(
      PropertyRole role,
      String logicalName,
      String externalName,
      String jsonapiName,
      boolean bindable) {
    return new ReadProperty<>(
        logicalName, new SemanticProperty(role, logicalName, externalName, jsonapiName), bindable);
  }

  /** Registers one read definition, partitioning properties by their metadata role. */
  @SafeVarargs
  final void define(String resourceType, ReadProperty<String>... properties) {
    ReadProperty<String> identifier = null;
    ReadProperty<String> localId = null;
    List<ReadProperty<String>> attributes = new ArrayList<>();
    List<ReadProperty<String>> relationships = new ArrayList<>();
    for (ReadProperty<String> property : properties) {
      switch (property.role()) {
        case ID -> identifier = property;
        case LOCAL_ID -> localId = property;
        case ATTRIBUTE -> attributes.add(property);
        case RELATIONSHIP -> relationships.add(property);
        case RESOURCE_META, RELATIONSHIP_META ->
            throw new IllegalArgumentException("read definition carries no meta properties");
      }
    }
    definitions.put(
        resourceType,
        new ReadResourceDefinition<>(resourceType, identifier, localId, attributes, relationships));
  }

  /** Sets one property token's diagnostic raw type; defaults to {@link Object}. */
  void rawType(String propertyToken, Class<?> rawType) {
    propertyRawTypes.put(propertyToken, rawType);
  }

  /** Configures one relationship property token's conversion result. */
  void relationshipConversion(String propertyToken, @Nullable Object value) {
    relationshipConversions.put(propertyToken, value);
  }

  /** Configures one relationship property token whose conversion throws. */
  void failRelationshipConversion(String propertyToken) {
    failingRelationshipTokens.add(propertyToken);
  }

  ReadResourceDefinition<String> definitionOrFail(String declaredType) {
    ReadResourceDefinition<String> definition = definitions.get(declaredType);
    if (definition == null) {
      throw new IllegalArgumentException("no definition for " + declaredType);
    }
    return definition;
  }

  @Override
  public Class<?> rawType(ReadProperty<String> property) {
    return propertyRawTypes.getOrDefault(property.token(), Object.class);
  }

  @Override
  public @Nullable Object parseIdentifier(String wireIdentifier) {
    parsedIdentifiers.add(wireIdentifier);
    if (parseThrows) {
      throw new IllegalStateException("parse failed for " + wireIdentifier);
    }
    if (parseReturnsNull) {
      return null;
    }
    return "parsed:" + wireIdentifier;
  }

  @Override
  public @Nullable Object convertRelationship(
      ReadProperty<String> property, RelationshipData data) {
    relationshipObservations.add(new RelationshipObservation(property.token(), data));
    if (failingRelationshipTokens.contains(property.token())) {
      throw new IllegalStateException("relationship conversion failed for " + property.token());
    }
    return relationshipConversions.getOrDefault(property.token(), "converted:" + property.token());
  }
}
