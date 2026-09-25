package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.RelationshipData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Mapping-local test double for {@link ReadResourceBackend} over string property and type tokens.
 * Test code configures definitions, per-property relationship shapes, and per-callback native
 * outcomes directly; no read semantics beyond that configuration are simulated.
 *
 * <p>It carries passive observations of the native callbacks the shared reader invokes: lazy
 * relationship-shape resolution, linkage-mapper invocation, and identifier-meta conversion. The
 * observers record invocations and return configured or deterministic results; they implement no
 * Jackson behavior.
 */
@NullMarked
final class MappingFakeReadResourceBackend implements ReadResourceBackend<String, String> {

  final Map<String, ReadResourceDefinition<String>> definitions = new LinkedHashMap<>();
  final Map<String, Class<?>> propertyRawTypes = new LinkedHashMap<>();

  /** Declared relationship shapes by relationship property token. */
  final Map<String, ReadRelationshipShape<String>> relationshipShapes = new LinkedHashMap<>();

  /** Declared linkage-mapper results by relationship property token. */
  final Map<String, @Nullable Object> linkageMappings = new LinkedHashMap<>();

  /** Relationship tokens whose configured linkage mapper throws. */
  final List<String> failingLinkageTokens = new ArrayList<>();

  /** Declared identifier-meta conversion results by wrapper meta token. */
  final Map<String, @Nullable Object> identifierMetaConversions = new LinkedHashMap<>();

  /** Wrapper meta tokens whose configured conversion throws. */
  final List<String> failingIdentifierMetaTokens = new ArrayList<>();

  /** Wire identifiers observed through {@link #parseIdentifier}, in call order. */
  final List<String> parsedIdentifiers = new ArrayList<>();

  /**
   * Relationship property tokens observed through {@link #readRelationshipShape}, in call order.
   */
  final List<String> shapeResolutions = new ArrayList<>();

  /** One linkage-mapper invocation: property token, data, and opaque target token. */
  record LinkageObservation(String propertyToken, RelationshipData data, String target) {}

  /** Linkage-mapper invocations of the shared reader, in call order. */
  final List<LinkageObservation> linkageObservations = new ArrayList<>();

  /** One identifier-meta conversion: property token, meta, meta token, and occurrence index. */
  record IdentifierMetaObservation(
      String propertyToken, Meta meta, String metaToken, int occurrenceIndex) {}

  /** Identifier-meta conversions of the shared reader, in call order. */
  final List<IdentifierMetaObservation> identifierMetaObservations = new ArrayList<>();

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
    ReadProperty<String> resourceMeta = null;
    List<ReadProperty<String>> attributes = new ArrayList<>();
    List<ReadProperty<String>> relationships = new ArrayList<>();
    List<ReadProperty<String>> relationshipMeta = new ArrayList<>();
    for (ReadProperty<String> property : properties) {
      switch (property.metadata().role()) {
        case ID -> identifier = property;
        case LOCAL_ID -> localId = property;
        case ATTRIBUTE -> attributes.add(property);
        case RELATIONSHIP -> relationships.add(property);
        case RESOURCE_META -> resourceMeta = property;
        case RELATIONSHIP_META -> relationshipMeta.add(property);
      }
    }
    definitions.put(
        resourceType,
        new ReadResourceDefinition<>(
            resourceType,
            identifier,
            localId,
            attributes,
            relationships,
            resourceMeta,
            relationshipMeta));
  }

  /** Sets one property token's diagnostic raw type; defaults to {@link Object}. */
  void rawType(String propertyToken, Class<?> rawType) {
    propertyRawTypes.put(propertyToken, rawType);
  }

  /** Configures one relationship property token's direct built-in shape. */
  void directRelationship(String propertyToken, boolean toMany) {
    relationshipShapes.put(propertyToken, new ReadRelationshipShape.Direct<>(toMany));
  }

  /** Configures one relationship property token's mapped shape with an opaque target token. */
  void mappedRelationship(String propertyToken, boolean toMany, String target) {
    relationshipShapes.put(propertyToken, new ReadRelationshipShape.Mapped<>(toMany, target));
  }

  /** Configures one relationship property token's wrapper shape. */
  void wrappedRelationship(
      String propertyToken,
      boolean toMany,
      String metaToken,
      ReadRelationshipShape<String> targetShape) {
    relationshipShapes.put(
        propertyToken, new ReadRelationshipShape.Wrapped<>(toMany, metaToken, targetShape));
  }

  /** Configures one relationship property token's linkage-mapper result. */
  void linkageMapping(String propertyToken, @Nullable Object value) {
    linkageMappings.put(propertyToken, value);
  }

  /** Configures one relationship property token whose linkage mapper throws. */
  void failLinkageMapping(String propertyToken) {
    failingLinkageTokens.add(propertyToken);
  }

  /** Configures one wrapper meta token's identifier-meta conversion result. */
  void identifierMetaConversion(String metaToken, @Nullable Object value) {
    identifierMetaConversions.put(metaToken, value);
  }

  /** Configures one wrapper meta token whose identifier-meta conversion throws. */
  void failIdentifierMetaConversion(String metaToken) {
    failingIdentifierMetaTokens.add(metaToken);
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
  public ReadRelationshipShape<String> readRelationshipShape(ReadProperty<String> property) {
    shapeResolutions.add(property.token());
    ReadRelationshipShape<String> shape = relationshipShapes.get(property.token());
    if (shape == null) {
      throw new IllegalStateException("no relationship shape for " + property.token());
    }
    return shape;
  }

  @Override
  public @Nullable Object mapLinkage(
      ReadProperty<String> property, RelationshipData data, String target) {
    linkageObservations.add(new LinkageObservation(property.token(), data, target));
    if (failingLinkageTokens.contains(property.token())) {
      throw new IllegalStateException("linkage mapping failed for " + property.token());
    }
    return linkageMappings.getOrDefault(property.token(), "mapped:" + property.token());
  }

  @Override
  public @Nullable Object convertIdentifierMeta(
      ReadProperty<String> property, Meta meta, String metaToken, int occurrenceIndex) {
    identifierMetaObservations.add(
        new IdentifierMetaObservation(property.token(), meta, metaToken, occurrenceIndex));
    if (failingIdentifierMetaTokens.contains(metaToken)) {
      throw new IllegalStateException("identifier meta conversion failed for " + metaToken);
    }
    return identifierMetaConversions.getOrDefault(metaToken, "meta:" + metaToken);
  }
}
