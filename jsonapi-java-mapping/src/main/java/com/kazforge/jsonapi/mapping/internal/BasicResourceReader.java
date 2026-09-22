package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.Attributes;
import com.kazforge.jsonapi.core.model.JsonApiMembers;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.Relationships;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.internal.mapping.ResourceTypeMatch;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Backend-neutral reader for the basic JSON:API Core-to-application read semantics.
 *
 * <p>It owns resource-type matching, strict and independent {@code id}/{@code lid} role selection,
 * wire-member lookup by JSON:API name, the distinction between an absent attribute and a present
 * JSON null, the distinction between an absent relationship (or absent relationship {@code data})
 * and present linkage, synthetic input keys by backend external name, resource-relative locations
 * for supplied members, and the shared non-deserializable and identifier-conversion diagnostics.
 * The resource-type authority stays the existing {@link ResourceTypeMatch}.
 *
 * <p>Read phase order is part of the contract: the caller resolves the mapping, then calls {@link
 * #requireResourceType} and validates all declared meta targets, then calls {@link #readBasic}.
 * Within {@link #readBasic}, the supplied {@code id} role, {@code lid} role, attributes in mapping
 * order, and relationships in mapping order are bound. A relationship whose member is absent, or
 * whose {@code data} member is absent, invokes no conversion callback. Whole-object meta and
 * relationship-meta binding remain adapter-owned and follow this result.
 *
 * @param <P> opaque backend-native property token
 */
@NullMarked
public final class BasicResourceReader<P> {

  private static final MappingLocation ID_LOCATION = MappingLocation.of(JsonApiMembers.ID);
  private static final MappingLocation LID_LOCATION = MappingLocation.of(JsonApiMembers.LID);

  private final ReadResourceBackend<P> backend;

  public BasicResourceReader(ReadResourceBackend<P> backend) {
    this.backend = Objects.requireNonNull(backend, "backend");
  }

  /**
   * Rejects a resource object whose wire type does not match the mapped resource type, through the
   * single shared {@link ResourceTypeMatch} authority.
   */
  public void requireResourceType(
      ResourceObject resource, ReadResourceDefinition<P> definition, Class<?> rawType) {
    Objects.requireNonNull(resource, "resource");
    Objects.requireNonNull(definition, "definition");
    Objects.requireNonNull(rawType, "rawType");
    ResourceTypeMatch.requireMatching(definition.resourceType(), resource, rawType);
  }

  /**
   * Binds the basic identity, attribute, and relationship members of one resource, in phase order,
   * and returns the nullable-value-preserving synthetic input map plus the supplied identity
   * locations. Resource-type matching is the caller's preceding step; whole-meta and
   * relationship-meta binding are the caller's following step.
   */
  public BasicReadResult readBasic(
      ResourceObject resource, ReadResourceDefinition<P> definition, Class<?> rawType) {
    Objects.requireNonNull(resource, "resource");
    Objects.requireNonNull(definition, "definition");
    Objects.requireNonNull(rawType, "rawType");
    Map<String, @Nullable Object> properties = new LinkedHashMap<>();
    // Strict role mapping: wire id binds only to the id role, wire lid only to the local-id role.
    // Neither member ever falls back into the other role's property.
    MappingLocation identifierLocation = null;
    ReadProperty<P> identifierProperty = definition.identifier();
    if (identifierProperty != null && resource.hasId()) {
      identifierLocation = ID_LOCATION;
      requireBindable(identifierProperty, identifierLocation, rawType);
      bindIdentifier(
          Objects.requireNonNull(resource.id()),
          identifierLocation,
          identifierProperty,
          properties);
    }
    MappingLocation localIdLocation = null;
    ReadProperty<P> localIdProperty = definition.localId();
    if (localIdProperty != null && resource.hasLid()) {
      localIdLocation = LID_LOCATION;
      requireBindable(localIdProperty, localIdLocation, rawType);
      bindIdentifier(
          Objects.requireNonNull(resource.lid()), localIdLocation, localIdProperty, properties);
    }
    bindAttributes(resource, definition, properties, rawType);
    bindRelationships(resource, definition, properties, rawType);
    return new BasicReadResult(properties, identifierLocation, localIdLocation);
  }

  private void bindIdentifier(
      String wireIdentifier,
      MappingLocation identifierLocation,
      ReadProperty<P> identifierProperty,
      Map<String, @Nullable Object> properties) {
    Object parsed;
    try {
      parsed = backend.parseIdentifier(wireIdentifier);
    } catch (RuntimeException e) {
      throw identifierConversionFailure(backend.rawType(identifierProperty), identifierLocation, e);
    }
    if (parsed == null) {
      throw identifierConversionFailure(
          backend.rawType(identifierProperty), identifierLocation, null);
    }
    // Keep the parsed JSON:API intermediate in the synthetic property map. The adapter's final bean
    // construction applies the target property's fully contextualized deserializer exactly once.
    properties.put(identifierProperty.externalName(), parsed);
  }

  private void bindAttributes(
      ResourceObject resource,
      ReadResourceDefinition<P> definition,
      Map<String, @Nullable Object> properties,
      Class<?> rawType) {
    if (definition.attributes().isEmpty()) {
      return;
    }
    Attributes attributes = resource.attributes();
    if (attributes == null) {
      return;
    }
    Map<String, @Nullable Object> members = attributes.attributes();
    for (ReadProperty<P> property : definition.attributes()) {
      if (!members.containsKey(property.jsonapiName())) {
        continue;
      }
      requireBindable(
          property, MappingLocation.of(JsonApiMembers.ATTRIBUTES, property.jsonapiName()), rawType);
      properties.put(property.externalName(), members.get(property.jsonapiName()));
    }
  }

  private void bindRelationships(
      ResourceObject resource,
      ReadResourceDefinition<P> definition,
      Map<String, @Nullable Object> properties,
      Class<?> rawType) {
    if (definition.relationships().isEmpty()) {
      return;
    }
    Relationships relationships = resource.relationships();
    if (relationships == null) {
      return;
    }
    for (ReadProperty<P> property : definition.relationships()) {
      Relationship relationship = relationships.relationships().get(property.jsonapiName());
      RelationshipData data = relationship == null ? null : relationship.data();
      if (data == null) {
        continue;
      }
      MappingLocation location =
          MappingLocation.of(
              JsonApiMembers.RELATIONSHIPS, property.jsonapiName(), JsonApiMembers.DATA);
      requireBindable(property, location, rawType);
      properties.put(property.externalName(), backend.convertRelationship(property, data));
    }
  }

  private static <P> void requireBindable(
      ReadProperty<P> property, MappingLocation location, Class<?> rawType) {
    if (property.bindable()) {
      return;
    }
    throw new JsonApiMappingException(
        MappingDiagnostic.NON_DESERIALIZABLE_PROPERTY,
        rawType,
        location,
        "Supplied JSON:API member at '"
            + location
            + "' targets property '"
            + property.logicalName()
            + "' without an effective deserialization target on "
            + rawType.getName());
  }

  /**
   * Builds the stable identifier-conversion diagnostic for a supplied identity member.
   *
   * <p>{@code diagnosticClass} is the class reported on the mapping failure, which is deliberately
   * not always the mapped identity property's type. A direct wire-identifier parse failure reports
   * the identity property's raw type, while a backend's construction-failure reclassification
   * reports the containing resource type; both callers share this diagnostic without changing what
   * each reports. Exposed to backend cooperation so the reclassification reports the same
   * identifier-conversion failure without re-deriving its message.
   */
  public static JsonApiMappingException identifierConversionFailure(
      Class<?> diagnosticClass, MappingLocation identifierLocation, @Nullable Throwable cause) {
    String message =
        cause == null
            ? "Identifier converter returned null for the wire identifier at '"
                + identifierLocation
                + "'"
            : "Failed to convert the wire identifier at '"
                + identifierLocation
                + "' for "
                + diagnosticClass.getName();
    return cause == null
        ? new JsonApiMappingException(
            MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED,
            diagnosticClass,
            identifierLocation,
            message)
        : new JsonApiMappingException(
            MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED,
            diagnosticClass,
            identifierLocation,
            message,
            cause);
  }
}
