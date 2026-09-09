package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.kazemek.jsonapi.core.model.Attributes;
import io.github.kazemek.jsonapi.core.model.JsonApiMembers;
import io.github.kazemek.jsonapi.core.model.Relationship;
import io.github.kazemek.jsonapi.core.model.RelationshipData;
import io.github.kazemek.jsonapi.core.model.Relationships;
import io.github.kazemek.jsonapi.core.model.ResourceObject;
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingLocation;
import io.github.kazemek.jsonapi.jackson.internal.mapping.ResourceTypeMatch;
import io.github.kazemek.jsonapi.jackson.mapping.IdentifierConverter;
import io.github.kazemek.jsonapi.jackson.patch.PatchChange;
import io.github.kazemek.jsonapi.jackson.patch.PatchCommand;
import io.github.kazemek.jsonapi.jackson2.RelationshipLinkageMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Binds a validated single-resource update into a {@link PatchCommand} without constructing a DTO.
 *
 * <p>Converts only supplied mapped attributes and relationships in attribute-then-relationship
 * encounter order. Reuses {@link ResourceMapping} definitions and the same relationship cardinality
 * / linkage rules as {@link RelationshipLinkageSupport}; per-member conversion delegates to {@link
 * PatchMemberConverter} with the property accessor type as the conversion target. Document {@code
 * included} is never read. A relationship member without {@code data} produces no change; {@code
 * readValue} rejects that shape earlier with {@code RELATIONSHIP_DATA_REQUIRED}, while {@code
 * fromDocument} skips it without re-validation. Identifier meta is not an independent {@link
 * PatchChange}; it rides on {@code ResourceIdentifier} values inside {@link
 * PatchChange.RelationshipChange} when linkage is supplied.
 *
 * <p>Recursive structured attributes use the {@link StructuredValueBinder}: a supplied attribute
 * whose declared type is an ordinary traversable structured domain value (or a single {@code
 * PatchPresence} wrapper / transparent {@code Optional} around one) and whose wire value is an
 * object binds to an {@link PatchChange.AttributeChange} carrying a {@link
 * io.github.kazemek.jsonapi.jackson.patch.StructuredPatch} of supplied-only nested changes instead
 * of a fully materialized replacement bean. Presence-aware PATCH shapes remain a typed-path concept
 * and are rejected on this path.
 */
public final class DomainPatchBinder {

  private static final MappingLocation ID_LOCATION = MappingLocation.of("id");

  private final MappingDefinitionCache cache;
  private final PatchMemberConverter converter;
  private final StructuredValueBinder structuredBinder;
  private final WholeMetaTarget wholeMetaTarget;

  public DomainPatchBinder(
      JsonMapper mapper,
      IdentifierConverter identifierConverter,
      MappingDefinitionCache cache,
      Map<Class<?>, RelationshipLinkageMapper> linkageMappers) {
    this.cache = Objects.requireNonNull(cache, "cache");
    this.converter = new PatchMemberConverter(mapper, identifierConverter, linkageMappers);
    this.structuredBinder = new StructuredValueBinder(mapper);
    this.wholeMetaTarget = new WholeMetaTarget(mapper);
  }

  /** Binds one resource object into a presence-aware patch command for {@code targetType}. */
  @SuppressWarnings("java:S1452")
  public PatchCommand<?> fromResource(ResourceObject resource, JavaType targetType) {
    Objects.requireNonNull(resource, "resource");
    Objects.requireNonNull(targetType, "targetType");
    Class<?> rawType = targetType.getRawClass();
    ResourceMapping mapping = cache.resolve(targetType);
    validateResourceType(resource, mapping, rawType);
    validateMetaTargets(mapping, rawType);
    Object identity = convertIdentity(resource, mapping, targetType, rawType);
    List<PatchChange> changes = new ArrayList<>();
    bindResourceMetaChange(resource, mapping, targetType, rawType, changes);
    bindAttributeChanges(resource, mapping, targetType, rawType, changes);
    bindRelationshipChanges(resource, mapping, targetType, rawType, changes);
    @SuppressWarnings({"rawtypes", "unchecked"})
    PatchCommand<?> command = new PatchCommand(rawType, identity, changes);
    return command;
  }

  private void validateResourceType(
      ResourceObject resource, ResourceMapping mapping, Class<?> rawType) {
    ResourceTypeMatch.requireMatching(mapping.resourceType(), resource, rawType);
  }

  /**
   * Whole-meta declared-target validation for the low-level domain-mapping role (read/write rule):
   * Bean / Map / Object with at most one {@link java.util.Optional} wrapper.
   */
  private void validateMetaTargets(ResourceMapping mapping, Class<?> rawType) {
    wholeMetaTarget.validateReadWriteTargets(mapping, rawType);
  }

  private Object convertIdentity(
      ResourceObject resource, ResourceMapping mapping, JavaType targetType, Class<?> rawType) {
    MappingProperty identifierProperty = mapping.identifierProperty();
    if (identifierProperty == null || !resource.hasId()) {
      throw new JsonApiMappingException(
          MappingDiagnostic.IDENTIFIER_CONVERSION_FAILED,
          rawType,
          ID_LOCATION,
          "Resource update identity requires a non-null id at '" + ID_LOCATION + "'");
    }
    return converter.convertIdentity(
        Objects.requireNonNull(resource.id()), identifierProperty, targetType, rawType);
  }

  private void bindAttributeChanges(
      ResourceObject resource,
      ResourceMapping mapping,
      JavaType targetType,
      Class<?> rawType,
      List<PatchChange> changes) {
    Attributes attributes = resource.attributes();
    if (attributes == null || mapping.attributes().isEmpty()) {
      return;
    }
    Map<String, MappingProperty> byJsonapiName =
        PatchMemberConverter.byJsonapiName(mapping.attributes());
    for (Map.Entry<String, @Nullable Object> entry : attributes.attributes().entrySet()) {
      MappingProperty property = byJsonapiName.get(entry.getKey());
      if (property == null) {
        continue;
      }
      Object rawValue = entry.getValue();
      Object value = bindAttributeValue(property, rawValue, targetType, rawType);
      changes.add(
          new PatchChange.AttributeChange(property.jsonapiName(), property.logicalName(), value));
    }
  }

  private @Nullable Object bindAttributeValue(
      MappingProperty property, @Nullable Object rawValue, JavaType targetType, Class<?> rawType) {
    JavaType declaredType = property.accessor().getType();
    if (rawValue == null) {
      return converter.convertAttribute(
          property, null, declaredType, rawType, targetType, attributeLocation(property));
    }
    MappingLocation location = attributeLocation(property);
    StructuredValueBinder.LowLevelKind kind =
        structuredBinder.lowLevelKind(
            declaredType,
            rawValue,
            property.accessor(),
            property.definition().getMutator(),
            location,
            rawType);
    if (kind == StructuredValueBinder.LowLevelKind.RECURSE) {
      return structuredBinder.bindLowLevelStructured(rawValue, declaredType, location, rawType);
    }
    return converter.convertAttribute(
        property,
        rawValue,
        PatchMemberConverter.unwrapPatchPresence(declaredType),
        rawType,
        targetType,
        location);
  }

  /** Resource-relative wire location of one top-level PATCH attribute, escaped per RFC 6901. */
  private static MappingLocation attributeLocation(MappingProperty property) {
    return MappingLocation.of("attributes", property.jsonapiName());
  }

  private void bindResourceMetaChange(
      ResourceObject resource,
      ResourceMapping mapping,
      JavaType targetType,
      Class<?> rawType,
      List<PatchChange> changes) {
    MappingProperty property = mapping.resourceMeta();
    if (property == null || resource.meta() == null) {
      return;
    }
    Object value =
        bindMetaValue(
            property,
            resource.meta().members(),
            RelationshipMetaSupport.resourceMetaLocation(),
            targetType,
            rawType);
    changes.add(
        new PatchChange.ResourceMetaChange(JsonApiMembers.META, property.logicalName(), value));
  }

  private @Nullable Object bindMetaValue(
      MappingProperty property,
      @Nullable Object rawValue,
      MappingLocation pointer,
      JavaType targetType,
      Class<?> rawType) {
    JavaType declaredType = property.accessor().getType();
    StructuredValueBinder.LowLevelKind kind =
        structuredBinder.lowLevelKind(
            declaredType,
            rawValue,
            property.accessor(),
            property.definition().getMutator(),
            pointer,
            rawType);
    if (kind == StructuredValueBinder.LowLevelKind.RECURSE) {
      return structuredBinder.bindLowLevelStructured(rawValue, declaredType, pointer, rawType);
    }
    return converter.convertWholeMeta(
        property,
        rawValue,
        PatchMemberConverter.unwrapPatchPresence(declaredType),
        targetType,
        pointer,
        rawType);
  }

  private void bindRelationshipChanges(
      ResourceObject resource,
      ResourceMapping mapping,
      JavaType targetType,
      Class<?> rawType,
      List<PatchChange> changes) {
    Relationships relationships = resource.relationships();
    if (relationships == null || mapping.relationships().isEmpty()) {
      return;
    }
    Map<String, MappingProperty> byJsonapiName =
        PatchMemberConverter.byJsonapiName(mapping.relationships());
    Map<String, MappingProperty> relationshipMetaByTarget =
        RelationshipMetaSupport.byTarget(mapping.relationshipMetaProperties());
    for (Map.Entry<String, Relationship> entry : relationships.relationships().entrySet()) {
      MappingProperty property = byJsonapiName.get(entry.getKey());
      if (property != null) {
        Relationship relationship = entry.getValue();
        RelationshipData data = relationship.data();
        if (data != null) {
          Object value =
              converter.convertRelationship(property, data, property.accessor().getType());
          changes.add(
              new PatchChange.RelationshipChange(
                  property.jsonapiName(), property.logicalName(), value));
          MappingProperty metaProperty = relationshipMetaByTarget.get(property.jsonapiName());
          if (metaProperty != null && relationship.meta() != null) {
            MappingLocation pointer =
                RelationshipMetaSupport.relationshipMetaLocation(property.jsonapiName());
            Object metaValue =
                bindMetaValue(
                    metaProperty, relationship.meta().members(), pointer, targetType, rawType);
            changes.add(
                new PatchChange.RelationshipMetaChange(
                    metaProperty.jsonapiName(), metaProperty.logicalName(), metaValue));
          }
        }
      }
    }
  }
}
