package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Mapping-local test double for {@link PatchResourceBackend} over string property and type tokens.
 * Test code configures definitions, per-property relationship shapes, and per-callback native
 * outcomes directly; no PATCH semantics beyond that configuration are simulated.
 *
 * <p>It carries passive observations of the native callbacks the shared binder invokes: declared
 * meta-target validation, identity/attribute/meta conversion, relationship coercion, lazy
 * relationship-shape resolution, linkage-mapper invocation, and identifier-meta conversion. The
 * observers record invocations and return configured or deterministic results; they implement no
 * Jackson behavior.
 */
@NullMarked
final class MappingFakePatchResourceBackend implements PatchResourceBackend<String, String> {

  final Map<String, PatchResourceDefinition<String>> definitions = new LinkedHashMap<>();

  /** Declared relationship shapes by relationship property token. */
  final Map<String, ReadRelationshipShape<String>> relationshipShapes = new LinkedHashMap<>();

  /** Declared linkage-mapper results by relationship property token. */
  final Map<String, @Nullable Object> linkageMappings = new LinkedHashMap<>();

  /** Declared attribute conversion results by attribute property token. */
  final Map<String, @Nullable Object> attributeValues = new LinkedHashMap<>();

  /** Declared whole-meta conversion results by meta property token. */
  final Map<String, @Nullable Object> metaValues = new LinkedHashMap<>();

  /** Declared identifier-meta conversion results by wrapper meta token. */
  final Map<String, @Nullable Object> identifierMetaConversions = new LinkedHashMap<>();

  /** Declared coerced relationship results by relationship property token. */
  final Map<String, @Nullable Object> coercedRelationships = new LinkedHashMap<>();

  /** Native callback invocations in order, for phase-order assertions. */
  final List<String> callOrder = new ArrayList<>();

  /** Resource types observed through declared meta-target validation. */
  final List<String> metaTargetValidations = new ArrayList<>();

  /** Wire identifiers observed through {@link #convertIdentity}. */
  final List<String> parsedIdentifiers = new ArrayList<>();

  /** One attribute/meta conversion: property token, raw value, and wire location. */
  record ConversionObservation(String propertyToken, @Nullable Object rawValue, String location) {}

  /** Attribute conversions of the shared binder, in call order. */
  final List<ConversionObservation> attributeConversions = new ArrayList<>();

  /** Whole-meta conversions of the shared binder, in call order. */
  final List<ConversionObservation> metaConversions = new ArrayList<>();

  /** Relationship property tokens observed through {@link #readRelationshipShape}. */
  final List<String> shapeResolutions = new ArrayList<>();

  /** One linkage-mapper invocation: property token, data, and opaque target token. */
  record LinkageObservation(String propertyToken, RelationshipData data, String target) {}

  /** Linkage-mapper invocations of the shared binder, in call order. */
  final List<LinkageObservation> linkageObservations = new ArrayList<>();

  /** One identifier-meta conversion: property token, meta, meta token, and occurrence index. */
  record IdentifierMetaObservation(
      String propertyToken, Meta meta, String metaToken, int occurrenceIndex) {}

  /** Identifier-meta conversions of the shared binder, in call order. */
  final List<IdentifierMetaObservation> identifierMetaObservations = new ArrayList<>();

  /** Relationship property tokens observed through {@link #coerceRelationship}. */
  final List<String> coercionInvocations = new ArrayList<>();

  boolean failMetaTargetValidation;

  static PatchProperty<String> property(
      PropertyRole role, String logicalName, String externalName, String jsonapiName) {
    return property(role, logicalName, externalName, jsonapiName, true);
  }

  static PatchProperty<String> property(
      PropertyRole role,
      String logicalName,
      String externalName,
      String jsonapiName,
      boolean bindable) {
    return new PatchProperty<>(
        logicalName, new SemanticProperty(role, logicalName, externalName, jsonapiName), bindable);
  }

  /** Registers one PATCH definition, partitioning properties by their metadata role. */
  @SafeVarargs
  final void define(String resourceType, PatchProperty<String>... properties) {
    PatchProperty<String> identifier = null;
    PatchProperty<String> resourceMeta = null;
    List<PatchProperty<String>> attributes = new ArrayList<>();
    List<PatchProperty<String>> relationships = new ArrayList<>();
    List<PatchProperty<String>> relationshipMeta = new ArrayList<>();
    for (PatchProperty<String> property : properties) {
      switch (property.metadata().role()) {
        case ID -> identifier = property;
        case LOCAL_ID -> throw new IllegalArgumentException("PATCH definitions carry no local id");
        case ATTRIBUTE -> attributes.add(property);
        case RELATIONSHIP -> relationships.add(property);
        case RESOURCE_META -> resourceMeta = property;
        case RELATIONSHIP_META -> relationshipMeta.add(property);
      }
    }
    definitions.put(
        resourceType,
        new PatchResourceDefinition<>(
            resourceType, identifier, attributes, relationships, resourceMeta, relationshipMeta));
  }

  PatchResourceDefinition<String> definitionOrFail(String resourceType) {
    PatchResourceDefinition<String> definition = definitions.get(resourceType);
    if (definition == null) {
      throw new IllegalArgumentException("no definition for " + resourceType);
    }
    return definition;
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

  /** Configures one wrapper meta token's identifier-meta conversion result. */
  void identifierMetaConversion(String metaToken, @Nullable Object value) {
    identifierMetaConversions.put(metaToken, value);
  }

  @Override
  public Class<?> rawType(PatchProperty<String> property) {
    return Object.class;
  }

  @Override
  public void validateDeclaredMetaTargets(
      PatchResourceDefinition<String> definition, Class<?> rawType) {
    metaTargetValidations.add(definition.resourceType());
    callOrder.add("validateMetaTargets");
    if (failMetaTargetValidation) {
      throw new IllegalStateException("declared meta targets invalid");
    }
  }

  @Override
  public Object convertIdentity(
      String wireIdentifier, PatchProperty<String> identifier, String beanType, Class<?> rawType) {
    parsedIdentifiers.add(wireIdentifier);
    callOrder.add("identity:" + identifier.token());
    return "identity:" + wireIdentifier;
  }

  @Override
  public @Nullable Object convertAttribute(
      PatchProperty<String> property,
      @Nullable Object rawValue,
      String beanType,
      MappingLocation location,
      Class<?> rawType) {
    attributeConversions.add(
        new ConversionObservation(property.token(), rawValue, location.pointer()));
    callOrder.add("attribute:" + property.token());
    return attributeValues.getOrDefault(property.token(), rawValue);
  }

  @Override
  public @Nullable Object convertWholeMeta(
      PatchProperty<String> property,
      @Nullable Object rawValue,
      String beanType,
      MappingLocation location,
      Class<?> rawType) {
    metaConversions.add(new ConversionObservation(property.token(), rawValue, location.pointer()));
    callOrder.add("meta:" + property.token());
    return metaValues.getOrDefault(property.token(), rawValue);
  }

  @Override
  public ReadRelationshipShape<String> readRelationshipShape(PatchProperty<String> property) {
    shapeResolutions.add(property.token());
    callOrder.add("shape:" + property.token());
    ReadRelationshipShape<String> shape = relationshipShapes.get(property.token());
    if (shape == null) {
      throw new IllegalStateException("no relationship shape for " + property.token());
    }
    return shape;
  }

  @Override
  public @Nullable Object mapLinkage(
      PatchProperty<String> property, RelationshipData data, String target) {
    linkageObservations.add(new LinkageObservation(property.token(), data, target));
    callOrder.add("linkage:" + property.token());
    return linkageMappings.getOrDefault(property.token(), "mapped:" + property.token());
  }

  @Override
  public @Nullable Object convertIdentifierMeta(
      PatchProperty<String> property, Meta meta, String metaToken, int occurrenceIndex) {
    identifierMetaObservations.add(
        new IdentifierMetaObservation(property.token(), meta, metaToken, occurrenceIndex));
    callOrder.add("identifierMeta:" + metaToken);
    return identifierMetaConversions.getOrDefault(metaToken, "meta:" + metaToken);
  }

  @Override
  public @Nullable Object coerceRelationship(
      PatchProperty<String> property, @Nullable Object value) {
    coercionInvocations.add(property.token());
    callOrder.add("coerce:" + property.token());
    return coercedRelationships.getOrDefault(property.token(), value);
  }
}
