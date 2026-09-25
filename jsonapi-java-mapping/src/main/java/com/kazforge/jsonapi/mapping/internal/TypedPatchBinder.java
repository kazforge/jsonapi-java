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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Backend-neutral orchestrator for binding a validated single-resource update directly into an
 * application-owned annotated PATCH DTO.
 *
 * <p>It owns the exact phase and diagnostic precedence: resource-type match, complete typed
 * declaration preflight, required {@code id} identity, attributes, relationships, resource meta,
 * relationship meta, then handoff to native DTO construction. The preflight covers the complete
 * typed declaration before identity is required or any supplied member is bound. Supplied unknown
 * members fail with {@link MappingDiagnostic#UNKNOWN_PATCH_MEMBER} at their escaped supplied wire
 * name; omitted members bind to {@code PatchPresence.omitted()}; relationship meta participates
 * only when the relationship carries {@code data}; document {@code included} is never read.
 *
 * <p>Native mechanics stay backend-owned and are reached through {@link TypedPatchBackend}:
 * declared relationship-linkage identifier-meta target validation, wire-identifier parsing,
 * configured relationship-linkage conversion, final bean construction, construction-path
 * extraction, and identifier-construction failure reclassification. Nested presence-aware member
 * binding is reached through {@link TypedMemberValueBinder}.
 *
 * <p>This type is unsupported implementation detail for backend cooperation, not consumer SPI, and
 * must not appear in supported backend public signatures.
 *
 * @param <P> opaque backend-native property token
 * @param <T> opaque backend-native type token
 */
@NullMarked
public final class TypedPatchBinder<P, T> {

  private static final MappingLocation ID_LOCATION = MappingLocation.of(JsonApiMembers.ID);
  private static final MappingLocation ATTRIBUTES_LOCATION =
      MappingLocation.of(JsonApiMembers.ATTRIBUTES);
  private static final MappingLocation RELATIONSHIPS_LOCATION =
      MappingLocation.of(JsonApiMembers.RELATIONSHIPS);

  private final TypedPatchBackend<P, T> backend;
  private final TypedMemberValueBinder<T> typedMemberBinder;

  public TypedPatchBinder(
      TypedPatchBackend<P, T> backend, TypedMemberValueBinder<T> typedMemberBinder) {
    this.backend = Objects.requireNonNull(backend, "backend");
    this.typedMemberBinder = Objects.requireNonNull(typedMemberBinder, "typedMemberBinder");
  }

  /** Binds one resource object into a PATCH DTO instance of {@code targetType}. */
  public Object bind(
      ResourceObject resource,
      TypedPatchDefinition<P, T> definition,
      T targetType,
      Class<?> rawType) {
    Objects.requireNonNull(resource, "resource");
    Objects.requireNonNull(definition, "definition");
    Objects.requireNonNull(targetType, "targetType");
    Objects.requireNonNull(rawType, "rawType");
    ResourceTypeMatch.requireMatching(definition.resourceType(), resource, rawType);
    validatePatchDtoDeclaration(definition, rawType);
    Map<String, @Nullable Object> properties = new LinkedHashMap<>();
    bindIdentity(resource, definition, properties, rawType);
    bindAttributes(resource, definition, properties, rawType);
    bindRelationships(resource, definition, properties, rawType);
    bindResourceMeta(resource, definition, properties, rawType);
    bindRelationshipMeta(resource, definition, properties, rawType);
    return backend.construct(targetType, properties, rawType, definition.identifier());
  }

  /** Resource-relative wire location of one top-level PATCH DTO attribute. */
  private static <P, T> MappingLocation attributeLocation(TypedPatchProperty<P, T> property) {
    return MappingLocation.of(JsonApiMembers.ATTRIBUTES, property.jsonapiName());
  }

  /** Resource-relative wire location of one top-level relationship's linkage. */
  private static <P, T> MappingLocation relationshipLocation(TypedPatchProperty<P, T> property) {
    return MappingLocation.of(
        JsonApiMembers.RELATIONSHIPS, property.jsonapiName(), JsonApiMembers.DATA);
  }

  /**
   * PATCH DTO declaration check: every mapped attribute and relationship member must be exactly
   * {@code PatchPresence<T>} and must not carry wrapper-level Jackson customization. Unannotated
   * ordinary properties do not participate and are not declaration-checked.
   */
  private void validatePatchDtoDeclaration(
      TypedPatchDefinition<P, T> definition, Class<?> rawType) {
    TypedPatchProperty<P, T> identifier = definition.identifier();
    if (identifier != null && identifier.patchPresence()) {
      throw new JsonApiMappingException(
          MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE,
          rawType,
          ID_LOCATION,
          "PATCH DTO identifier '"
              + identifier.logicalName()
              + "' must not be declared as PatchPresence on "
              + rawType.getName());
    }
    for (TypedPatchProperty<P, T> property : definition.attributes()) {
      validatePatchableProperty(property, rawType, attributeLocation(property));
    }
    for (TypedPatchProperty<P, T> property : definition.relationships()) {
      validatePatchableProperty(property, rawType, relationshipLocation(property));
    }
    TypedPatchProperty<P, T> resourceMeta = definition.resourceMeta();
    if (resourceMeta != null) {
      validatePatchableMetaProperty(resourceMeta, rawType, resourceMetaLocation());
    }
    for (TypedPatchProperty<P, T> property : definition.relationshipMetaProperties()) {
      validatePatchableMetaProperty(
          property, rawType, relationshipMetaLocation(property.jsonapiName()));
    }
    backend.validateRelationshipLinkageMeta(definition, rawType);
  }

  private void validatePatchableProperty(
      TypedPatchProperty<P, T> property, Class<?> rawType, MappingLocation memberLocation) {
    if (!property.patchPresence() || property.wrapperCustomization()) {
      throw new JsonApiMappingException(
          MappingDiagnostic.INVALID_PATCH_PROPERTY_TYPE,
          rawType,
          memberLocation,
          "PATCH DTO member '"
              + property.logicalName()
              + "' must be declared exactly as PatchPresence<T> without wrapper-level "
              + "@JsonDeserialize/@JsonSerialize customization on "
              + rawType.getName());
    }
  }

  /**
   * Whole-meta member validation for the typed PATCH DTO role: the shared patchable member
   * authority (exactly {@code PatchPresence<T>}, no wrapper-level customization) plus, after
   * unwrapping one {@code PatchPresence} and at most one {@link java.util.Optional}, an effective
   * Bean / Map / Object target.
   */
  private void validatePatchableMetaProperty(
      TypedPatchProperty<P, T> property, Class<?> rawType, MappingLocation memberLocation) {
    validatePatchableProperty(property, rawType, memberLocation);
    if (!property.validMetaTarget()) {
      throw new JsonApiMappingException(
          MappingDiagnostic.INVALID_META_TARGET,
          rawType,
          memberLocation,
          "PATCH DTO meta member '"
              + property.logicalName()
              + "' must be PatchPresence<Bean|Map|Object> (with at most one Optional inside) on "
              + rawType.getName());
    }
  }

  private void bindIdentity(
      ResourceObject resource,
      TypedPatchDefinition<P, T> definition,
      Map<String, @Nullable Object> properties,
      Class<?> rawType) {
    TypedPatchProperty<P, T> identifier = definition.identifier();
    if (identifier == null || !resource.hasId()) {
      throw new JsonApiMappingException(
          MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED,
          rawType,
          ID_LOCATION,
          "Resource update identity requires a non-null id at '" + ID_LOCATION + "'");
    }
    Object identity = backend.parseIdentity(Objects.requireNonNull(resource.id()), rawType);
    properties.put(identifier.externalName(), identity);
  }

  private void bindAttributes(
      ResourceObject resource,
      TypedPatchDefinition<P, T> definition,
      Map<String, @Nullable Object> properties,
      Class<?> rawType) {
    Attributes attributes = resource.attributes();
    Map<String, @Nullable Object> supplied = attributes == null ? null : attributes.attributes();
    Map<String, TypedPatchProperty<P, T>> byJsonapiName = byJsonapiName(definition.attributes());
    if (supplied != null) {
      for (String name : supplied.keySet()) {
        if (!byJsonapiName.containsKey(name)) {
          throw unknownPatchMember(rawType, ATTRIBUTES_LOCATION.append(name), "attribute", name);
        }
      }
    }
    for (TypedPatchProperty<P, T> property : definition.attributes()) {
      if (supplied != null && supplied.containsKey(property.jsonapiName())) {
        Object value =
            typedMemberBinder.typedMemberValue(
                supplied.get(property.jsonapiName()),
                property.declaredType(),
                attributeLocation(property),
                rawType);
        properties.put(property.externalName(), new PresenceMarker(true, value));
      } else {
        properties.put(property.externalName(), new PresenceMarker(false, null));
      }
    }
  }

  private void bindRelationships(
      ResourceObject resource,
      TypedPatchDefinition<P, T> definition,
      Map<String, @Nullable Object> properties,
      Class<?> rawType) {
    Relationships relationships = resource.relationships();
    Map<String, Relationship> supplied =
        relationships == null ? null : relationships.relationships();
    Map<String, TypedPatchProperty<P, T>> byJsonapiName = byJsonapiName(definition.relationships());
    if (supplied != null) {
      for (String name : supplied.keySet()) {
        if (!byJsonapiName.containsKey(name)) {
          throw unknownPatchMember(
              rawType, RELATIONSHIPS_LOCATION.append(name), "relationship", name);
        }
      }
    }
    for (TypedPatchProperty<P, T> property : definition.relationships()) {
      Relationship relationship = supplied == null ? null : supplied.get(property.jsonapiName());
      RelationshipData data = relationship == null ? null : relationship.data();
      if (data == null) {
        // Omitted, or a supplied mapped relationship lacking data (only reachable on the
        // non-revalidating fromDocument path): bind as Omitted, mirroring the low-level skip.
        properties.put(property.externalName(), new PresenceMarker(false, null));
        continue;
      }
      Object value = backend.convertRelationship(property, data);
      properties.put(property.externalName(), new PresenceMarker(true, value));
    }
  }

  /**
   * Binds supplied resource meta as a {@code PatchPresence} member. Supplied meta without a
   * declared {@code @JsonApiMeta} member is rejected on the strict typed path.
   */
  private void bindResourceMeta(
      ResourceObject resource,
      TypedPatchDefinition<P, T> definition,
      Map<String, @Nullable Object> properties,
      Class<?> rawType) {
    TypedPatchProperty<P, T> property = definition.resourceMeta();
    if (property == null) {
      if (resource.meta() != null) {
        throw unknownPatchMember(rawType, resourceMetaLocation(), "meta", JsonApiMembers.META);
      }
      return;
    }
    if (resource.meta() == null) {
      properties.put(property.externalName(), new PresenceMarker(false, null));
      return;
    }
    Object value =
        typedMemberBinder.typedMemberValue(
            resource.meta().members(), property.declaredType(), resourceMetaLocation(), rawType);
    properties.put(property.externalName(), new PresenceMarker(true, value));
  }

  /**
   * Binds supplied relationship meta for each mapped relationship-meta member. Meta participates
   * only when the relationship carries {@code data}; supplied meta for a mapped relationship
   * without a declared {@code @JsonApiRelationshipMeta} member is rejected on the strict typed
   * path.
   */
  private void bindRelationshipMeta(
      ResourceObject resource,
      TypedPatchDefinition<P, T> definition,
      Map<String, @Nullable Object> properties,
      Class<?> rawType) {
    Relationships relationships = resource.relationships();
    Map<String, Relationship> supplied =
        relationships == null ? null : relationships.relationships();
    Map<String, TypedPatchProperty<P, T>> metaByTarget =
        byTarget(definition.relationshipMetaProperties());
    if (supplied != null) {
      for (Map.Entry<String, Relationship> entry : supplied.entrySet()) {
        Relationship relationship = entry.getValue();
        if (relationship.data() != null
            && relationship.meta() != null
            && !metaByTarget.containsKey(entry.getKey())) {
          throw unknownPatchMember(
              rawType,
              relationshipMetaLocation(entry.getKey()),
              "relationship meta",
              entry.getKey());
        }
      }
    }
    for (TypedPatchProperty<P, T> property : definition.relationshipMetaProperties()) {
      Relationship relationship = supplied == null ? null : supplied.get(property.jsonapiName());
      if (relationship == null || relationship.data() == null || relationship.meta() == null) {
        properties.put(property.externalName(), new PresenceMarker(false, null));
        continue;
      }
      MappingLocation location = relationshipMetaLocation(property.jsonapiName());
      Object value =
          typedMemberBinder.typedMemberValue(
              relationship.meta().members(), property.declaredType(), location, rawType);
      properties.put(property.externalName(), new PresenceMarker(true, value));
    }
  }

  private static <P, T> Map<String, TypedPatchProperty<P, T>> byJsonapiName(
      List<TypedPatchProperty<P, T>> properties) {
    Map<String, TypedPatchProperty<P, T>> byName = new LinkedHashMap<>();
    for (TypedPatchProperty<P, T> property : properties) {
      byName.put(property.jsonapiName(), property);
    }
    return byName;
  }

  private static <P, T> Map<String, TypedPatchProperty<P, T>> byTarget(
      List<TypedPatchProperty<P, T>> relationshipMetaProperties) {
    Map<String, TypedPatchProperty<P, T>> byName = new LinkedHashMap<>();
    for (TypedPatchProperty<P, T> property : relationshipMetaProperties) {
      byName.put(property.jsonapiName(), property);
    }
    return byName;
  }

  /** Resource-relative diagnostic location for the resource-side {@code meta} member. */
  private static MappingLocation resourceMetaLocation() {
    return MappingLocation.of(JsonApiMembers.META);
  }

  /** Resource-relative diagnostic location for a specific relationship's {@code meta} member. */
  private static MappingLocation relationshipMetaLocation(String relationshipName) {
    return MappingLocation.of(JsonApiMembers.RELATIONSHIPS, relationshipName, JsonApiMembers.META);
  }

  private static JsonApiMappingException unknownPatchMember(
      Class<?> rawType, MappingLocation path, String kind, String name) {
    return new JsonApiMappingException(
        MappingDiagnostic.UNKNOWN_PATCH_MEMBER,
        rawType,
        path,
        "Unknown supplied " + kind + " '" + name + "' for PATCH DTO " + rawType.getName());
  }
}
