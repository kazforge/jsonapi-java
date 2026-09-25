package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.Attributes;
import com.kazforge.jsonapi.core.model.JsonApiMembers;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.Relationships;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.patch.PatchChange;
import com.kazforge.jsonapi.patch.PatchCommand;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Backend-neutral binder for the low-level JSON:API Core-to-application PATCH semantics.
 *
 * <p>It owns resource-type matching, required {@code id} identity (never a {@code lid} fallback),
 * supplied-member lookup by JSON:API name, bindability enforcement, {@link PatchChange}
 * construction, and {@link PatchCommand} assembly. The phase order is part of the contract: the
 * caller resolves the dedicated inbound PATCH definition, then this binder performs resource-type
 * matching, backend declared meta-target validation, identity conversion, resource meta, attributes
 * in wire encounter order, and relationships in wire encounter order. Each relationship-meta change
 * is emitted immediately after its relationship change and only when that relationship supplies
 * {@code data}. Omitted mapped members produce no change; an explicit attribute JSON null produces
 * a present change with a null value.
 *
 * <p>Native conversion stays backend-owned and is reached through {@link PatchResourceBackend}:
 * declared meta-target validation, identity parsing/conversion, recursive structured attribute and
 * meta conversion, final relationship container coercion, lazy relationship-shape resolution,
 * configured linkage-mapper invocation, and identifier-meta conversion. Whole linkage replacement,
 * cardinality, direct identifier copies, wrapper occurrence orchestration, and identifier-meta
 * sequencing are shared with ordinary reads through {@link RelationshipLinkageBinder}.
 *
 * <p>This type is unsupported implementation detail for backend cooperation, not consumer SPI, and
 * must not appear in supported backend public signatures.
 *
 * @param <T> opaque backend-native type token
 * @param <N> opaque backend-native property token
 */
@NullMarked
public final class PatchCommandBinder<T, N> {

  private static final MappingLocation ID_LOCATION = MappingLocation.of(JsonApiMembers.ID);

  private final PatchResourceBackend<T, N> backend;
  private final RelationshipLinkageBinder<T, PatchProperty<N>> linkageBinder;

  public PatchCommandBinder(PatchResourceBackend<T, N> backend) {
    this.backend = Objects.requireNonNull(backend, "backend");
    this.linkageBinder = new RelationshipLinkageBinder<>(backend);
  }

  /**
   * Binds one resource object into a presence-aware patch command for {@code rawType}. The caller
   * supplies the dedicated inbound PATCH definition and its opaque native bean type token.
   */
  @SuppressWarnings({"rawtypes", "unchecked", "java:S1452"})
  public PatchCommand<?> bind(
      ResourceObject resource,
      PatchResourceDefinition<N> definition,
      T beanType,
      Class<?> rawType) {
    Objects.requireNonNull(resource, "resource");
    Objects.requireNonNull(definition, "definition");
    Objects.requireNonNull(beanType, "beanType");
    Objects.requireNonNull(rawType, "rawType");
    ResourceTypeMatch.requireMatching(definition.resourceType(), resource, rawType);
    backend.validateDeclaredMetaTargets(definition, rawType);
    Object identity = convertIdentity(resource, definition, beanType, rawType);
    List<PatchChange> changes = new ArrayList<>();
    bindResourceMeta(resource, definition, beanType, rawType, changes);
    bindAttributes(resource, definition, beanType, rawType, changes);
    bindRelationships(resource, definition, beanType, rawType, changes);
    return new PatchCommand(rawType, identity, changes);
  }

  private Object convertIdentity(
      ResourceObject resource,
      PatchResourceDefinition<N> definition,
      T beanType,
      Class<?> rawType) {
    PatchProperty<N> identifier = definition.identifier();
    if (identifier == null || !resource.hasId()) {
      throw new JsonApiMappingException(
          MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED,
          rawType,
          ID_LOCATION,
          "Resource update identity requires a non-null id at '" + ID_LOCATION + "'");
    }
    requireBindable(identifier, ID_LOCATION, rawType);
    return backend.convertIdentity(
        Objects.requireNonNull(resource.id()), identifier, beanType, rawType);
  }

  private void bindResourceMeta(
      ResourceObject resource,
      PatchResourceDefinition<N> definition,
      T beanType,
      Class<?> rawType,
      List<PatchChange> changes) {
    PatchProperty<N> property = definition.resourceMeta();
    Meta meta = resource.meta();
    if (property == null || meta == null) {
      return;
    }
    MappingLocation location = MappingLocation.of(JsonApiMembers.META);
    requireBindable(property, location, rawType);
    Object value = backend.convertWholeMeta(property, meta.members(), beanType, location, rawType);
    changes.add(
        new PatchChange.ResourceMetaChange(JsonApiMembers.META, property.logicalName(), value));
  }

  private void bindAttributes(
      ResourceObject resource,
      PatchResourceDefinition<N> definition,
      T beanType,
      Class<?> rawType,
      List<PatchChange> changes) {
    Attributes attributes = resource.attributes();
    if (attributes == null || definition.attributes().isEmpty()) {
      return;
    }
    Map<String, PatchProperty<N>> byJsonapiName = byJsonapiName(definition.attributes());
    for (Map.Entry<String, @Nullable Object> entry : attributes.attributes().entrySet()) {
      PatchProperty<N> property = byJsonapiName.get(entry.getKey());
      if (property == null) {
        continue;
      }
      MappingLocation location =
          MappingLocation.of(JsonApiMembers.ATTRIBUTES, property.jsonapiName());
      requireBindable(property, location, rawType);
      Object value =
          backend.convertAttribute(property, entry.getValue(), beanType, location, rawType);
      changes.add(
          new PatchChange.AttributeChange(property.jsonapiName(), property.logicalName(), value));
    }
  }

  private void bindRelationships(
      ResourceObject resource,
      PatchResourceDefinition<N> definition,
      T beanType,
      Class<?> rawType,
      List<PatchChange> changes) {
    Relationships relationships = resource.relationships();
    if (relationships == null || definition.relationships().isEmpty()) {
      return;
    }
    Map<String, PatchProperty<N>> byJsonapiName = byJsonapiName(definition.relationships());
    Map<String, PatchProperty<N>> relationshipMetaByTarget =
        byTarget(definition.relationshipMetaProperties());
    for (Map.Entry<String, Relationship> entry : relationships.relationships().entrySet()) {
      PatchProperty<N> property = byJsonapiName.get(entry.getKey());
      if (property == null) {
        continue;
      }
      Relationship relationship = entry.getValue();
      RelationshipData data = relationship.data();
      if (data != null) {
        MappingLocation location =
            MappingLocation.of(
                JsonApiMembers.RELATIONSHIPS, property.jsonapiName(), JsonApiMembers.DATA);
        requireBindable(property, location, rawType);
        Object value = backend.coerceRelationship(property, linkageBinder.bind(property, data));
        changes.add(
            new PatchChange.RelationshipChange(
                property.jsonapiName(), property.logicalName(), value));
        PatchProperty<N> metaProperty = relationshipMetaByTarget.get(property.jsonapiName());
        if (metaProperty != null && relationship.meta() != null) {
          MappingLocation metaLocation =
              MappingLocation.of(
                  JsonApiMembers.RELATIONSHIPS, property.jsonapiName(), JsonApiMembers.META);
          requireBindable(metaProperty, metaLocation, rawType);
          Object metaValue =
              backend.convertWholeMeta(
                  metaProperty, relationship.meta().members(), beanType, metaLocation, rawType);
          changes.add(
              new PatchChange.RelationshipMetaChange(
                  metaProperty.jsonapiName(), metaProperty.logicalName(), metaValue));
        }
      }
    }
  }

  private static <N> Map<String, PatchProperty<N>> byJsonapiName(
      List<PatchProperty<N>> properties) {
    Map<String, PatchProperty<N>> byName = new LinkedHashMap<>();
    for (PatchProperty<N> property : properties) {
      byName.put(property.jsonapiName(), property);
    }
    return byName;
  }

  /**
   * Builds a target-relationship-jsonapi-name to relationship-meta property map. The adapter-local
   * resolver guarantees at most one relationship-meta property per target, so keys are unique.
   */
  private static <N> Map<String, PatchProperty<N>> byTarget(
      List<PatchProperty<N>> relationshipMetaProperties) {
    Map<String, PatchProperty<N>> byName = new LinkedHashMap<>();
    for (PatchProperty<N> property : relationshipMetaProperties) {
      byName.put(property.jsonapiName(), property);
    }
    return byName;
  }

  private static <N> void requireBindable(
      PatchProperty<N> property, MappingLocation location, Class<?> rawType) {
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
}
