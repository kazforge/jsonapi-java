package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.Attributes;
import com.kazforge.jsonapi.core.model.JsonApiMembers;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.Relationships;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.representation.FieldPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Backend-neutral writer for basic domain-to-core resource semantics.
 *
 * <p>It owns fieldset validation and filtering, strict versus create identity rules, empty-member
 * omission, attribute and member naming, ordinary domain-object to-one/to-many identifier
 * construction, relationship {@code data} construction, and base {@link ResourceObject} assembly.
 * Native type resolution, property access, configured conversion, identifiers, relationship
 * normalization, and enrichment stay behind {@link WriteResourceBackend}; resource meta and
 * decoration are applied by the backend around this writer, not by it.
 *
 * <p>Write phase order is part of the contract: the backend validates its meta targets before
 * calling {@link #writeBasic}, each selected relationship's linkage is enriched immediately after
 * it is built, and the caller applies resource meta after {@link #writeBasic} returns.
 *
 * @param <T> opaque backend-native type token
 * @param <P> opaque backend-native property token
 */
@NullMarked
public final class BasicResourceWriter<T, P> {

  private static final String RESOURCE = "resource";
  private static final String DECLARED_TYPE = "declaredType";
  private static final String DOMAIN = "domain";

  private final WriteResourceBackend<T, P> backend;

  public BasicResourceWriter(WriteResourceBackend<T, P> backend) {
    this.backend = Objects.requireNonNull(backend, "backend");
  }

  /**
   * Validates a present fieldset against the mapped write definition before any selective read. An
   * empty fieldset selects no fields and is not validated field by field.
   */
  public void validateFieldset(
      Object resource, T declaredType, List<String> fields, FieldPolicy fieldPolicy) {
    Objects.requireNonNull(resource, RESOURCE);
    Objects.requireNonNull(declaredType, DECLARED_TYPE);
    Objects.requireNonNull(fields, "fields");
    Objects.requireNonNull(fieldPolicy, "fieldPolicy");
    if (fields.isEmpty()) {
      return;
    }
    WriteResourceDefinition<P> definition = backend.definition(declaredType);
    Set<String> mappedNames = new HashSet<>();
    for (WriteProperty<P> property : definition.attributes()) {
      mappedNames.add(property.jsonapiName());
    }
    for (WriteProperty<P> property : definition.relationships()) {
      mappedNames.add(property.jsonapiName());
    }
    for (String name : fields) {
      if (!mappedNames.contains(name)) {
        // Fieldset specification failures have no document member location; the offending field
        // name stays in the message per the mapping-location contract.
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.INVALID_FIELDSET_FIELD,
            resource.getClass(),
            "Unknown fieldset field '" + name + "' on " + definition.resourceType());
      }
      if (!fieldPolicy.allows(definition.resourceType(), name)) {
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.DENIED_FIELDSET_FIELD,
            resource.getClass(),
            "Fieldset field denied for " + definition.resourceType() + "." + name);
      }
    }
  }

  /**
   * Renders one resource's basic members. {@code allowAbsentIdentity} selects create-request
   * leniency: when both identity roles yield nothing the resource maps with absent {@code id} and
   * {@code lid} instead of failing, leaving that decision to core {@code CREATE_REQUEST}
   * validation. Present values convert and fail identically on both paths.
   */
  public ResourceObject writeBasic(
      Object resource,
      T declaredType,
      @Nullable Set<String> allowedFields,
      boolean allowAbsentIdentity) {
    Objects.requireNonNull(resource, RESOURCE);
    Objects.requireNonNull(declaredType, DECLARED_TYPE);
    WriteResourceDefinition<P> definition = backend.definition(declaredType);
    IdentityValues identity =
        allowAbsentIdentity
            ? new IdentityValues(
                extractIdValue(resource, definition.identifier()),
                extractLocalIdValue(resource, definition.localId()))
            : requireIdentity(resource, definition);
    Attributes attributes = buildAttributes(resource, declaredType, definition, allowedFields);
    Relationships relationships =
        buildRelationships(resource, declaredType, definition, allowedFields);
    return new ResourceObject(
        definition.resourceType(),
        identity.id(),
        identity.localId(),
        attributes.isEmpty() ? null : attributes,
        relationships.isEmpty() ? null : relationships,
        null,
        null,
        Map.of());
  }

  /**
   * Extracts the JSON:API identity of one mapped domain object using the strict identity rule:
   * {@code id} and {@code lid} stay independent and a resource with neither value fails.
   */
  public ResourceIdentifier identifier(Object domain, T declaredType) {
    Objects.requireNonNull(domain, DOMAIN);
    Objects.requireNonNull(declaredType, DECLARED_TYPE);
    WriteResourceDefinition<P> definition = backend.definition(declaredType);
    IdentityValues identity = requireIdentity(domain, definition);
    return new ResourceIdentifier(
        definition.resourceType(), identity.id(), identity.localId(), null, Map.of());
  }

  /** Reads only the mapped {@code id} role; a null value means the member is absent. */
  public @Nullable String extractId(Object domain, T declaredType) {
    Objects.requireNonNull(domain, DOMAIN);
    Objects.requireNonNull(declaredType, DECLARED_TYPE);
    return extractIdValue(domain, backend.definition(declaredType).identifier());
  }

  /** Reads only the mapped {@code lid} role; a null value means the member is absent. */
  public @Nullable String extractLocalId(Object domain, T declaredType) {
    Objects.requireNonNull(domain, DOMAIN);
    Objects.requireNonNull(declaredType, DECLARED_TYPE);
    return extractLocalIdValue(domain, backend.definition(declaredType).localId());
  }

  private IdentityValues requireIdentity(Object domain, WriteResourceDefinition<P> definition) {
    String id = extractIdValue(domain, definition.identifier());
    String localId = extractLocalIdValue(domain, definition.localId());
    if (id == null && localId == null) {
      throw missingIdentity(domain, definition);
    }
    return new IdentityValues(id, localId);
  }

  private @Nullable String extractIdValue(
      Object domain, @Nullable WriteProperty<P> identifierProperty) {
    return identityValue(domain, identifierProperty, JsonApiMembers.ID, "Identifier");
  }

  private @Nullable String extractLocalIdValue(
      Object domain, @Nullable WriteProperty<P> localIdProperty) {
    return identityValue(domain, localIdProperty, JsonApiMembers.LID, "Local-id");
  }

  /**
   * Reads one identity role through the backend. A present value that converts to no wire string
   * fails at its own role's wire location, exactly like the missing-identity diagnostic family.
   */
  private @Nullable String identityValue(
      Object domain, @Nullable WriteProperty<P> property, String wireName, String roleLabel) {
    if (property == null) {
      return null;
    }
    IdentityRead read = backend.identity(domain, property);
    if (!read.present()) {
      return null;
    }
    String value = read.value();
    if (value == null) {
      throw missingIdentifier(
          domain.getClass(),
          MappingLocation.of(wireName),
          roleLabel + " converter returned null for property '" + property.logicalName() + "'");
    }
    return value;
  }

  private static JsonApiMappingException missingIdentity(
      Object domain, WriteResourceDefinition<?> definition) {
    WriteProperty<?> idProperty = definition.identifier();
    if (idProperty != null) {
      return missingIdentifier(
          domain.getClass(),
          MappingLocation.of(JsonApiMembers.ID),
          "Identifier property '" + idProperty.logicalName() + "' is null");
    }
    // The resolver guarantees at least one identity role, so a missing id role implies a lid role.
    WriteProperty<?> localIdProperty = Objects.requireNonNull(definition.localId(), "localId");
    return missingIdentifier(
        domain.getClass(),
        MappingLocation.of(JsonApiMembers.LID),
        "Local-id property '" + localIdProperty.logicalName() + "' is null");
  }

  private static JsonApiMappingException missingIdentifier(
      Class<?> type, MappingLocation location, String message) {
    return new JsonApiMappingException(
        MappingDiagnostic.MISSING_IDENTIFIER, type, location, message);
  }

  private Attributes buildAttributes(
      Object resource,
      T declaredType,
      WriteResourceDefinition<P> definition,
      @Nullable Set<String> allowedFields) {
    if (definition.attributes().isEmpty()) {
      return Attributes.empty();
    }
    Map<String, @Nullable Object> attributes = new LinkedHashMap<>();
    for (WriteProperty<P> property : definition.attributes()) {
      if (allowedFields == null || allowedFields.contains(property.jsonapiName())) {
        AttributeConversion converted = backend.attribute(resource, declaredType, property);
        if (converted.emitted()) {
          attributes.put(property.jsonapiName(), converted.value());
        }
      }
    }
    return Attributes.ofAttributes(attributes);
  }

  private Relationships buildRelationships(
      Object resource,
      T declaredType,
      WriteResourceDefinition<P> definition,
      @Nullable Set<String> allowedFields) {
    if (definition.relationships().isEmpty()) {
      return Relationships.empty();
    }
    Map<String, @Nullable Relationship> relationships = new LinkedHashMap<>();
    for (WriteProperty<P> property : definition.relationships()) {
      if (allowedFields != null && !allowedFields.contains(property.jsonapiName())) {
        continue;
      }
      RelationshipData linkage = buildLinkage(resource, declaredType, property);
      relationships.put(
          property.jsonapiName(),
          backend.enrichRelationship(resource, declaredType, property, linkage));
    }
    return Relationships.ofRelationships(relationships);
  }

  private RelationshipData buildLinkage(
      Object resource, T declaredType, WriteProperty<P> property) {
    RelationshipValue<T> normalized =
        backend.normalizeRelationship(resource, declaredType, property);
    return switch (normalized) {
      case RelationshipValue.ToOne<T>(Object target, T targetType) ->
          target == null
              ? RelationshipData.NullLinkage.INSTANCE
              : new RelationshipData.SingleLinkage(
                  identifier(target, effectiveType(target, targetType)));
      case RelationshipValue.ToMany<T>(List<Object> targets, T targetType) ->
          buildToManyLinkage(targets, targetType);
      case RelationshipValue.Linkage<T>(RelationshipData data) -> data;
    };
  }

  private RelationshipData buildToManyLinkage(
      List<Object> targets, @Nullable T declaredTargetType) {
    if (targets.isEmpty()) {
      return RelationshipData.IdentifierCollectionLinkage.empty();
    }
    T targetType = Objects.requireNonNull(declaredTargetType, "declaredTargetType");
    List<ResourceIdentifier> identifiers = new ArrayList<>(targets.size());
    for (Object target : targets) {
      identifiers.add(identifier(target, effectiveType(target, targetType)));
    }
    return new RelationshipData.IdentifierCollectionLinkage(identifiers);
  }

  private T effectiveType(Object domain, @Nullable T declaredType) {
    return backend.effectiveType(domain, Objects.requireNonNull(declaredType, DECLARED_TYPE));
  }

  /** Extracted identity-role values; either may be null when its member is absent. */
  private record IdentityValues(@Nullable String id, @Nullable String localId) {}
}
