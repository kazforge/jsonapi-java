package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.Attributes;
import com.kazforge.jsonapi.core.model.JsonApiMembers;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.Relationships;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.internal.mapping.IdentifierMetaSupport;
import com.kazforge.jsonapi.internal.mapping.ResourceTypeMatch;
import com.kazforge.jsonapi.mapping.RelationshipLinkage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Backend-neutral reader for the JSON:API Core-to-application read semantics, basic and advanced.
 *
 * <p>It owns resource-type matching, strict and independent {@code id}/{@code lid} role selection,
 * wire-member lookup by JSON:API name, the distinction between an absent attribute and a present
 * JSON null, the distinction between an absent relationship (or absent relationship {@code data})
 * and present linkage, synthetic input keys by backend external name, resource-relative locations
 * for supplied members, and the shared non-deserializable and identifier-conversion diagnostics. It
 * additionally owns the advanced relationship phase: cardinality validation, null/empty
 * short-circuiting, direct {@link ResourceIdentifier} copies that preserve identifier meta and drop
 * additional members, opt-in {@link RelationshipLinkage} occurrence orchestration with
 * per-occurrence target/meta pairing, and resource/relationship meta presence and raw-member
 * binding. The resource-type authority stays the existing {@link ResourceTypeMatch}.
 *
 * <p>Read phase order is part of the contract: the caller resolves the mapping, then calls {@link
 * #requireResourceType} and validates all declared meta targets, then calls {@link #readBasic}.
 * Within {@link #readBasic}, the supplied {@code id} role, {@code lid} role, attributes in mapping
 * order, relationships in mapping order, resource meta, and relationship meta in mapping order are
 * bound. A relationship whose member is absent, or whose {@code data} member is absent, invokes no
 * relationship-shape or conversion callback. Native relationship target/type resolution and mapper
 * selection stay backend-owned and are reached lazily through {@link ReadResourceBackend} only
 * after supplied {@code data} is present and bindable.
 *
 * @param <T> opaque backend-native type token
 * @param <P> opaque backend-native property token
 */
@NullMarked
public final class BasicResourceReader<T, P> {

  private static final MappingLocation ID_LOCATION = MappingLocation.of(JsonApiMembers.ID);
  private static final MappingLocation LID_LOCATION = MappingLocation.of(JsonApiMembers.LID);
  private static final MappingLocation RESOURCE_META_LOCATION =
      MappingLocation.of(JsonApiMembers.META);

  private final ReadResourceBackend<T, P> backend;

  public BasicResourceReader(ReadResourceBackend<T, P> backend) {
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
   * Binds the identity, attribute, relationship, and meta members of one resource, in phase order,
   * and returns the nullable-value-preserving synthetic input map plus the supplied identity
   * locations. Resource-type matching is the caller's preceding step; native bean construction is
   * the caller's following step.
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
    bindResourceMeta(resource, definition, properties, rawType);
    bindRelationshipMeta(resource, definition, properties, rawType);
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
      properties.put(property.externalName(), bindRelationship(property, data));
    }
  }

  /**
   * Binds one present relationship's linkage. Relationship-shape resolution (native target/type
   * resolution plus mapper selection) happens only here, after the caller established supplied
   * {@code data} and bindability, so an unsupported or unresolvable target still fails before the
   * shared cardinality and null/empty short-circuit checks and no mapper sees empty linkage.
   */
  private @Nullable Object bindRelationship(ReadProperty<P> property, RelationshipData data) {
    ReadRelationshipShape<T> shape = backend.readRelationshipShape(property);
    if (shape instanceof ReadRelationshipShape.Wrapped<T> wrapped) {
      return bindWrapper(property, wrapped, data);
    }
    boolean empty = validateCardinality(property, data, shape.toMany());
    if (empty) {
      return shape.toMany() ? List.of() : null;
    }
    if (shape instanceof ReadRelationshipShape.Direct<T>) {
      return copyDirectLinkage(data);
    }
    ReadRelationshipShape.Mapped<T> mapped = (ReadRelationshipShape.Mapped<T>) shape;
    return backend.mapLinkage(property, data, mapped.target());
  }

  private @Nullable Object bindWrapper(
      ReadProperty<P> property, ReadRelationshipShape.Wrapped<T> wrapped, RelationshipData data) {
    return wrapped.toMany()
        ? bindWrappedToMany(property, wrapped, data)
        : bindWrappedToOne(property, wrapped, data);
  }

  private @Nullable Object bindWrappedToOne(
      ReadProperty<P> property, ReadRelationshipShape.Wrapped<T> wrapped, RelationshipData data) {
    boolean empty = validateCardinality(property, data, false);
    if (empty) {
      return null;
    }
    Object target = mapWrapperTarget(property, wrapped.targetShape(), data);
    if (target == null) {
      return null;
    }
    Object meta = convertOccurrenceMeta(property, wrapped, singleIdentifier(data), -1);
    return new RelationshipLinkage<>(target, meta);
  }

  private Object bindWrappedToMany(
      ReadProperty<P> property, ReadRelationshipShape.Wrapped<T> wrapped, RelationshipData data) {
    boolean empty = validateCardinality(property, data, true);
    List<ResourceIdentifier> identifiers =
        data instanceof RelationshipData.IdentifierCollectionLinkage(List<ResourceIdentifier> ids)
            ? ids
            : List.of();
    if (empty) {
      return List.of();
    }
    List<Object> values = new ArrayList<>(identifiers.size());
    for (int index = 0; index < identifiers.size(); index++) {
      ResourceIdentifier identifier = identifiers.get(index);
      Object target =
          mapWrapperTarget(
              property, wrapped.targetShape(), new RelationshipData.SingleLinkage(identifier));
      if (target == null) {
        throw linkageMappingFailed(property, index);
      }
      Object meta = convertOccurrenceMeta(property, wrapped, identifier, index);
      values.add(new RelationshipLinkage<>(target, meta));
    }
    return values;
  }

  /**
   * Maps one wrapper occurrence's target through the declared target shape: a direct target copies
   * the identifier (preserving identifier meta and dropping additional members), while a mapped
   * target delegates to the backend's configured mapper with the occurrence's own linkage.
   */
  private @Nullable Object mapWrapperTarget(
      ReadProperty<P> property, ReadRelationshipShape<T> targetShape, RelationshipData data) {
    if (targetShape instanceof ReadRelationshipShape.Direct<T>) {
      ResourceIdentifier identifier = singleIdentifier(data);
      return identifier == null ? null : IdentifierMetaSupport.copyLinkageIdentifier(identifier);
    }
    ReadRelationshipShape.Mapped<T> mapped = (ReadRelationshipShape.Mapped<T>) targetShape;
    return backend.mapLinkage(property, data, mapped.target());
  }

  private @Nullable Object convertOccurrenceMeta(
      ReadProperty<P> property,
      ReadRelationshipShape.Wrapped<T> wrapped,
      @Nullable ResourceIdentifier identifier,
      int occurrenceIndex) {
    if (identifier == null || identifier.meta() == null) {
      return null;
    }
    return backend.convertIdentifierMeta(
        property, Objects.requireNonNull(identifier.meta()), wrapped.meta(), occurrenceIndex);
  }

  private static @Nullable ResourceIdentifier singleIdentifier(RelationshipData data) {
    return data instanceof RelationshipData.SingleLinkage(ResourceIdentifier identifier)
        ? identifier
        : null;
  }

  /**
   * Copies one direct relationship linkage, preserving each identifier's type, id, lid, and meta
   * and dropping its additional members. Empty to-many linkage is short-circuited by the caller.
   */
  private static @Nullable Object copyDirectLinkage(RelationshipData data) {
    return switch (data) {
      case RelationshipData.NullLinkage ignored -> null;
      case RelationshipData.SingleLinkage(ResourceIdentifier identifier) ->
          IdentifierMetaSupport.copyLinkageIdentifier(identifier);
      case RelationshipData.IdentifierCollectionLinkage(List<ResourceIdentifier> identifiers) -> {
        List<Object> values = new ArrayList<>(identifiers.size());
        for (ResourceIdentifier identifier : identifiers) {
          values.add(IdentifierMetaSupport.copyLinkageIdentifier(identifier));
        }
        yield values;
      }
    };
  }

  private void bindResourceMeta(
      ResourceObject resource,
      ReadResourceDefinition<P> definition,
      Map<String, @Nullable Object> properties,
      Class<?> rawType) {
    ReadProperty<P> property = definition.resourceMeta();
    Meta meta = resource.meta();
    if (property == null || meta == null) {
      return;
    }
    requireBindable(property, RESOURCE_META_LOCATION, rawType);
    properties.put(property.externalName(), meta.members());
  }

  private void bindRelationshipMeta(
      ResourceObject resource,
      ReadResourceDefinition<P> definition,
      Map<String, @Nullable Object> properties,
      Class<?> rawType) {
    if (definition.relationshipMetaProperties().isEmpty()) {
      return;
    }
    Relationships relationships = resource.relationships();
    if (relationships == null) {
      return;
    }
    for (ReadProperty<P> property : definition.relationshipMetaProperties()) {
      Relationship relationship = relationships.relationships().get(property.jsonapiName());
      Meta meta = relationship == null ? null : relationship.meta();
      if (meta == null) {
        continue;
      }
      MappingLocation location =
          MappingLocation.of(
              JsonApiMembers.RELATIONSHIPS, property.jsonapiName(), JsonApiMembers.META);
      requireBindable(property, location, rawType);
      properties.put(property.externalName(), meta.members());
    }
  }

  /**
   * Validates linkage shape against the property's declared cardinality through the shared
   * cardinality authority, throwing {@link MappingDiagnostic#RELATIONSHIP_CARDINALITY_MISMATCH} for
   * illegal combinations. Returns whether the linkage denotes an empty value ({@code null} on
   * to-one, empty collection on to-many).
   */
  private boolean validateCardinality(
      ReadProperty<P> property, RelationshipData data, boolean toMany) {
    return switch (data) {
      case RelationshipData.NullLinkage ignored -> {
        if (toMany) {
          throw cardinalityMismatch(property, "null linkage on to-many relationship");
        }
        yield true;
      }
      case RelationshipData.SingleLinkage ignored -> {
        if (toMany) {
          throw cardinalityMismatch(property, "single linkage on to-many relationship");
        }
        yield false;
      }
      case RelationshipData.IdentifierCollectionLinkage(List<ResourceIdentifier> identifiers) -> {
        boolean empty = identifiers.isEmpty();
        if (!toMany) {
          throw cardinalityMismatch(
              property,
              empty
                  ? "empty collection linkage on to-one relationship"
                  : "collection linkage on to-one relationship");
        }
        yield empty;
      }
    };
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

  private JsonApiMappingException cardinalityMismatch(ReadProperty<P> property, String detail) {
    return new JsonApiMappingException(
        MappingDiagnostic.RELATIONSHIP_CARDINALITY_MISMATCH,
        backend.rawType(property),
        MappingLocation.of(
            JsonApiMembers.RELATIONSHIPS, property.jsonapiName(), JsonApiMembers.DATA),
        "Cardinality mismatch for relationship '" + property.logicalName() + "': " + detail);
  }

  private JsonApiMappingException linkageMappingFailed(ReadProperty<P> property, int index) {
    return new JsonApiMappingException(
        MappingDiagnostic.LINKAGE_MAPPING_FAILED,
        backend.rawType(property),
        MappingLocation.of(
            JsonApiMembers.RELATIONSHIPS,
            property.jsonapiName(),
            JsonApiMembers.DATA,
            Integer.toString(index)),
        "Relationship linkage mapper returned null for relationship '"
            + property.logicalName()
            + "'");
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
