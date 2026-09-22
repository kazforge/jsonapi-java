package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.Attributes;
import com.kazforge.jsonapi.core.model.JsonApiMembers;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.Relationships;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.core.validation.JsonApiValidationException;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.internal.mapping.IdentifierMetaSupport;
import com.kazforge.jsonapi.mapping.RelationshipLinkage;
import com.kazforge.jsonapi.representation.FieldPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Backend-neutral writer for basic and advanced domain-to-core write semantics.
 *
 * <p>It owns fieldset validation and filtering, strict versus create identity rules, attribute
 * orchestration, ordinary domain-object to-one/to-many linkage construction, advanced
 * relationship-value normalization (see {@link #relationshipData}), relationship-member assembly,
 * resource/relationship/identifier meta construction and attachment, member naming, and base {@link
 * ResourceObject} assembly. Configured conversion stays in the backend's own write orchestration
 * reached through the {@link WriteResourceBackend} capability boundary; additive link decoration is
 * applied by {@link ResourceDecorationWriter} after {@link #writeBasic} returns.
 *
 * <p>Write phase order is part of the contract: the backend validates its declared meta targets
 * before calling {@link #writeBasic}; within the shared write, all declared meta targets are
 * validated before any basic member read, each selected relationship's linkage read and
 * normalization precedes that relationship's meta conversion, and one relationship's meta completes
 * before the next relationship property is read. Resource meta is applied independently of
 * fieldsets, and additive link decoration is applied by {@link ResourceDecorationWriter} after
 * {@link #writeBasic} returns.
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
   * Renders one resource's members, applying resource meta independently of any fieldset. {@code
   * allowAbsentIdentity} selects create-request leniency: when both identity roles yield nothing
   * the resource maps with absent {@code id} and {@code lid} instead of failing, leaving that
   * decision to core {@code CREATE_REQUEST} validation. Present values convert and fail identically
   * on both paths.
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
    ResourceObject base =
        new ResourceObject(
            definition.resourceType(),
            identity.id(),
            identity.localId(),
            attributes.isEmpty() ? null : attributes,
            relationships.isEmpty() ? null : relationships,
            null,
            null,
            Map.of());
    Meta meta = buildResourceMeta(resource, declaredType, definition);
    if (meta == null) {
      return base;
    }
    return withMeta(base, meta);
  }

  @SuppressWarnings("NullAway")
  private static ResourceObject withMeta(ResourceObject base, Meta meta) {
    return new ResourceObject(
        base.type(),
        base.id(),
        base.lid(),
        base.attributes(),
        base.relationships(),
        base.links(),
        meta,
        base.additionalMembers());
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

  /**
   * Builds ordinary to-one linkage for one domain target, resolving its effective type and strict
   * identity through the backend.
   */
  public RelationshipData singleLinkage(Object target, T declaredType) {
    return new RelationshipData.SingleLinkage(
        identifier(target, backend.effectiveType(target, declaredType)));
  }

  /**
   * Builds ordinary to-many linkage for domain targets in value order, resolving each target's
   * effective type and strict identity through the backend. An empty list stays present-empty
   * to-many linkage.
   */
  public RelationshipData collectionLinkage(List<?> targets, T declaredType) {
    if (targets.isEmpty()) {
      return RelationshipData.IdentifierCollectionLinkage.empty();
    }
    List<ResourceIdentifier> identifiers = new ArrayList<>(targets.size());
    for (Object target : targets) {
      Object nonNullTarget = Objects.requireNonNull(target);
      identifiers.add(
          identifier(nonNullTarget, backend.effectiveType(nonNullTarget, declaredType)));
    }
    return new RelationshipData.IdentifierCollectionLinkage(identifiers);
  }

  /**
   * Normalizes one advanced relationship value into core linkage, owning the runtime normalization
   * and core-model decisions both adapters previously duplicated: one outer {@link Optional},
   * {@link List}, object-array, and {@link Iterable} materialization, null/empty linkage states,
   * direct {@link ResourceIdentifier} pass-through, direct to-one {@link RelationshipData}
   * pass-through, ordinary target linkage, {@link RelationshipLinkage} target recursion,
   * per-occurrence ordering, null-item skipping, and direct-identifier/domain-object mixed-value
   * rejection.
   *
   * <p>Declared cardinality and backend-native type tokens come from the {@link RelationshipShape}
   * and stay opaque. The ordinary target token is never resolved or validated eagerly: the
   * backend's target resolution is invoked only when normalization selects the ordinary
   * domain-object branch, while null/empty, all-null to-many, direct-identifier, and direct-data
   * branches return without consulting it. Wrapper identifier-meta conversion is requested through
   * the backend's declared-type conversion operation; occurrences without a meta value are returned
   * without enrichment.
   *
   * <p>After a {@link RelationshipLinkage} occurrence's target is mapped, to-one linkage is
   * required before wrapper meta is considered: explicit-null and collection linkage fail
   * consistently with {@link MappingDiagnostic#INVALID_IDENTIFIER_META_TARGET} at the occurrence's
   * identifier-meta location, including when the wrapper meta value is absent. Primitive arrays and
   * unsupported to-many containers still fail, optional to-many and nested transport shapes gain no
   * new support, to-one null stays {@code NullLinkage}, null/empty/all-null to-many stays
   * present-empty collection linkage, and direct identifier collections retain all supplied
   * members.
   */
  public RelationshipData relationshipData(
      Object resource,
      WriteProperty<P> property,
      RelationshipShape<T> shape,
      @Nullable Object value) {
    if (shape.toMany()) {
      return toManyRelationshipData(resource, property, shape, value);
    }
    return toOneRelationshipData(resource, property, shape, value, false);
  }

  private RelationshipData toOneRelationshipData(
      Object resource,
      WriteProperty<P> property,
      RelationshipShape<T> shape,
      @Nullable Object rawValue,
      boolean relationshipToMany) {
    Object value = unwrapOptional(rawValue);
    if (shape instanceof RelationshipShape.Wrapped<T> wrapped) {
      return toOneWrapperLinkage(resource, property, wrapped, value, relationshipToMany);
    }
    return switch (value) {
      case null -> RelationshipData.NullLinkage.INSTANCE;
      case ResourceIdentifier resourceIdentifier ->
          new RelationshipData.SingleLinkage(resourceIdentifier);
      case RelationshipData relationshipData -> relationshipData;
      default ->
          singleLinkage(
              Objects.requireNonNull(value),
              resolveTarget(
                  value,
                  shape.ordinaryTarget(),
                  relationshipToMany,
                  relationshipLocation(property)));
    };
  }

  private RelationshipData toOneWrapperLinkage(
      Object resource,
      WriteProperty<P> property,
      RelationshipShape.Wrapped<T> wrapped,
      @Nullable Object value,
      boolean relationshipToMany) {
    if (value == null) {
      return RelationshipData.NullLinkage.INSTANCE;
    }
    if (!(value instanceof RelationshipLinkage<?, ?>(Object target, Object meta))) {
      throw new JsonApiMappingException(
          MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_VALUE,
          value.getClass(),
          relationshipLocation(property),
          "Relationship '"
              + property.logicalName()
              + "' requires RelationshipLinkage values, got "
              + value.getClass().getName());
    }
    RelationshipData mappedTarget =
        toOneRelationshipData(
            resource, property, wrapped.targetShape(), target, relationshipToMany);
    return new RelationshipData.SingleLinkage(
        requireWrapperSingleIdentifier(resource, property, mappedTarget, meta, wrapped.meta(), -1));
  }

  private RelationshipData toManyRelationshipData(
      Object resource,
      WriteProperty<P> property,
      RelationshipShape<T> shape,
      @Nullable Object value) {
    if (value == null) {
      return RelationshipData.IdentifierCollectionLinkage.empty();
    }
    List<@Nullable Object> items = materializeToMany(value, relationshipLocation(property));
    if (shape instanceof RelationshipShape.Wrapped<T> wrapped) {
      return toManyWrapperLinkage(resource, property, wrapped, items);
    }
    if (items.isEmpty()) {
      return RelationshipData.IdentifierCollectionLinkage.empty();
    }
    boolean hasIdentifier = false;
    List<ResourceIdentifier> identifiers = new ArrayList<>();
    List<Object> domainObjects = new ArrayList<>();
    for (Object item : items) {
      if (item == null) {
        continue;
      }
      if (item instanceof ResourceIdentifier resourceIdentifier) {
        hasIdentifier = true;
        identifiers.add(resourceIdentifier);
      } else {
        domainObjects.add(item);
      }
    }
    if (hasIdentifier && !domainObjects.isEmpty()) {
      throw mixedToManyElements(domainObjects.getFirst(), relationshipLocation(property));
    }
    if (hasIdentifier) {
      return new RelationshipData.IdentifierCollectionLinkage(identifiers);
    }
    if (domainObjects.isEmpty()) {
      return RelationshipData.IdentifierCollectionLinkage.empty();
    }
    return collectionLinkage(
        domainObjects,
        resolveTarget(
            domainObjects.getFirst(),
            shape.ordinaryTarget(),
            true,
            relationshipLocation(property)));
  }

  private RelationshipData toManyWrapperLinkage(
      Object resource,
      WriteProperty<P> property,
      RelationshipShape.Wrapped<T> wrapped,
      List<@Nullable Object> items) {
    if (items.isEmpty()) {
      return RelationshipData.IdentifierCollectionLinkage.empty();
    }
    List<ResourceIdentifier> identifiers = new ArrayList<>();
    int occurrenceIndex = 0;
    for (Object item : items) {
      Object occurrence = unwrapOptional(item);
      if (occurrence == null) {
        continue;
      }
      if (!(occurrence instanceof RelationshipLinkage<?, ?>(Object target, Object meta))) {
        throw new JsonApiMappingException(
            MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_VALUE,
            occurrence.getClass(),
            relationshipLocation(property),
            "To-many RelationshipLinkage collection contains " + occurrence.getClass().getName());
      }
      RelationshipData mappedTarget =
          toOneRelationshipData(resource, property, wrapped.targetShape(), target, true);
      identifiers.add(
          requireWrapperSingleIdentifier(
              resource, property, mappedTarget, meta, wrapped.meta(), occurrenceIndex));
      occurrenceIndex++;
    }
    return new RelationshipData.IdentifierCollectionLinkage(identifiers);
  }

  /**
   * Requires to-one linkage for one wrapper occurrence before wrapper meta is considered, then
   * applies identifier-meta enrichment only when the occurrence carries a meta value. Explicit-null
   * and collection targets fail consistently at the occurrence's identifier-meta location,
   * including when the wrapper meta is absent.
   */
  private ResourceIdentifier requireWrapperSingleIdentifier(
      Object resource,
      WriteProperty<P> property,
      RelationshipData mappedTarget,
      @Nullable Object occurrenceMeta,
      @Nullable T declaredMetaToken,
      int occurrenceIndex) {
    String relationshipName = property.jsonapiName();
    if (!(mappedTarget instanceof RelationshipData.SingleLinkage(ResourceIdentifier identifier))) {
      throw new JsonApiMappingException(
          MappingDiagnostic.INVALID_IDENTIFIER_META_TARGET,
          resource.getClass(),
          identifierMetaLocation(relationshipName, occurrenceIndex),
          "RelationshipLinkage requires a mappable target for relationship '"
              + relationshipName
              + "'");
    }
    if (occurrenceMeta == null) {
      return identifier;
    }
    Objects.requireNonNull(declaredMetaToken, "declaredMetaToken");
    return overlayIdentifierMeta(
        resource, relationshipName, occurrenceIndex, declaredMetaToken, occurrenceMeta, identifier);
  }

  /**
   * Applies one wrapper occurrence's identifier meta onto the mapped to-one identifier.
   * Non-emission leaves the identifier's own meta in place, an emitted JSON null is an
   * authoritative overlay that clears it, an emitted object replaces it wholesale, any other
   * emitted shape fails, and conversion failures keep the stable identifier-meta diagnostics at the
   * occurrence's location.
   */
  private ResourceIdentifier overlayIdentifierMeta(
      Object resource,
      String relationshipName,
      int occurrenceIndex,
      T declaredMetaToken,
      Object occurrenceMeta,
      ResourceIdentifier identifier) {
    MappingLocation location = identifierMetaLocation(relationshipName, occurrenceIndex);
    MemberConversion converted;
    try {
      converted = backend.convertIdentifierMeta(declaredMetaToken, occurrenceMeta);
    } catch (RuntimeException e) {
      throw new JsonApiMappingException(
          MappingDiagnostic.INVALID_META_TARGET,
          resource.getClass(),
          location,
          "Failed to convert identifier meta for relationship '" + relationshipName + "'",
          e);
    }
    if (!converted.emitted()) {
      return identifier;
    }
    return IdentifierMetaSupport.withMeta(
        identifier, metaFromConverted(converted.value(), resource, location));
  }

  private static List<@Nullable Object> materializeToMany(
      Object value, MappingLocation relationshipLocation) {
    return switch (value) {
      case List<?> list -> {
        List<Object> result = new ArrayList<>(list.size());
        result.addAll(list);
        yield result;
      }
      case Object[] array -> {
        List<Object> result = new ArrayList<>(array.length);
        Collections.addAll(result, array);
        yield result;
      }
      case Iterable<?> iterable -> {
        List<Object> result = new ArrayList<>();
        for (Object item : iterable) {
          result.add(item);
        }
        yield result;
      }
      default ->
          throw new JsonApiMappingException(
              MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_VALUE,
              value.getClass(),
              relationshipLocation,
              "To-many relationship value is not a supported collection type: "
                  + value.getClass().getName());
    };
  }

  private T resolveTarget(
      @Nullable Object target,
      @Nullable T declaredTarget,
      boolean relationshipToMany,
      MappingLocation relationshipLocation) {
    return backend.resolveRelationshipTarget(
        target, declaredTarget, relationshipToMany, relationshipLocation);
  }

  private static MappingLocation relationshipLocation(WriteProperty<?> property) {
    return MappingLocation.of(
        JsonApiMembers.RELATIONSHIPS, property.jsonapiName(), JsonApiMembers.DATA);
  }

  private static MappingLocation relationshipMetaLocation(String relationshipName) {
    return MappingLocation.of(JsonApiMembers.RELATIONSHIPS, relationshipName, JsonApiMembers.META);
  }

  private static MappingLocation identifierMetaLocation(
      String relationshipName, int occurrenceIndex) {
    return occurrenceIndex < 0
        ? IdentifierMetaSupport.identifierMetaLocation(relationshipName)
        : IdentifierMetaSupport.identifierMetaLocation(relationshipName, occurrenceIndex);
  }

  private static JsonApiMappingException mixedToManyElements(
      Object firstNonResourceIdentifier, MappingLocation relationshipLocation) {
    return new JsonApiMappingException(
        MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_VALUE,
        firstNonResourceIdentifier.getClass(),
        relationshipLocation,
        "Mixed element types in to-many relationship collection: expected ResourceIdentifier, got "
            + firstNonResourceIdentifier.getClass().getName());
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
    Object value = unwrapOptional(backend.readValue(domain, property));
    if (value == null) {
      return null;
    }
    String converted = backend.convertIdentifier(value);
    if (converted == null) {
      throw missingIdentifier(
          domain.getClass(),
          MappingLocation.of(wireName),
          roleLabel + " converter returned null for property '" + property.logicalName() + "'");
    }
    return converted;
  }

  private static @Nullable Object unwrapOptional(@Nullable Object value) {
    if (value instanceof Optional<?> optional) {
      return optional.orElse(null);
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
        MemberConversion converted = backend.convertAttribute(resource, declaredType, property);
        if (converted.emitted()) {
          attributes.put(property.jsonapiName(), converted.value());
        }
      }
    }
    return Attributes.ofAttributes(attributes);
  }

  /**
   * Applies the single mapped resource-meta property independently of any fieldset, after
   * attributes and relationships are built.
   */
  private @Nullable Meta buildResourceMeta(
      Object resource, T declaredType, WriteResourceDefinition<P> definition) {
    WriteProperty<P> property = definition.resourceMeta();
    if (property == null) {
      return null;
    }
    return buildWholeMeta(
        resource, declaredType, property, MappingLocation.of(JsonApiMembers.META));
  }

  /**
   * Filters the mapped relationships by the selected fields, then builds each selected relationship
   * in declaration order: read and normalize linkage, then convert and attach the matched
   * relationship meta.
   */
  private Relationships buildRelationships(
      Object resource,
      T declaredType,
      WriteResourceDefinition<P> definition,
      @Nullable Set<String> allowedFields) {
    if (definition.relationships().isEmpty()) {
      return Relationships.empty();
    }
    List<WriteProperty<P>> selected = new ArrayList<>();
    for (WriteProperty<P> property : definition.relationships()) {
      if (allowedFields == null || allowedFields.contains(property.jsonapiName())) {
        selected.add(property);
      }
    }
    if (selected.isEmpty()) {
      return Relationships.empty();
    }
    Map<String, @Nullable Relationship> relationships = new LinkedHashMap<>();
    for (WriteProperty<P> property : selected) {
      relationships.put(
          property.jsonapiName(), buildRelationship(resource, declaredType, definition, property));
    }
    return Relationships.ofRelationships(relationships);
  }

  private Relationship buildRelationship(
      Object resource,
      T declaredType,
      WriteResourceDefinition<P> definition,
      WriteProperty<P> property) {
    Object value = backend.readValue(resource, property);
    RelationshipShape<T> shape = backend.relationshipShape(property);
    RelationshipData linkage = relationshipData(resource, property, shape, value);
    Meta meta = null;
    WriteProperty<P> metaProperty = definition.relationshipMetaFor(property.jsonapiName());
    if (metaProperty != null) {
      meta =
          buildWholeMeta(
              resource,
              declaredType,
              metaProperty,
              relationshipMetaLocation(property.jsonapiName()));
    }
    return new Relationship(linkage, null, meta, Map.of());
  }

  /**
   * Converts one present whole-meta property value into a core {@link Meta}. The converted result
   * must be a {@link Map}; scalar/array/non-object runtime values fail with a stable meta
   * diagnostic (never a leaked cast or core-validation failure). Failures report the
   * location-specific {@code metaLocation}.
   */
  private @Nullable Meta buildWholeMeta(
      Object resource, T declaredType, WriteProperty<P> property, MappingLocation metaLocation) {
    Object rawValue = backend.readValue(resource, property);
    Object value = unwrapOptional(rawValue);
    if (value == null) {
      return null;
    }
    MemberConversion converted;
    try {
      converted = backend.convertWholeMeta(resource, declaredType, property, rawValue, value);
    } catch (RuntimeException e) {
      throw metaValueFailure(resource, metaLocation, "Failed to convert meta value", e);
    }
    if (!converted.emitted()) {
      return null;
    }
    Object convertedValue = converted.value();
    if (!(convertedValue instanceof Map<?, ?> map)) {
      throw metaValueFailure(
          resource,
          metaLocation,
          "Converted meta value is not an object (expected a JSON object, got "
              + convertedTypeName(convertedValue)
              + ")",
          null);
    }
    try {
      return Meta.of(castMembers(map, resource, metaLocation));
    } catch (JsonApiValidationException e) {
      throw metaValueFailure(resource, metaLocation, "Invalid meta members", e);
    }
  }

  /**
   * Builds identifier meta from a converted wrapper value. A converted Java null is the
   * authoritative emitted-JSON-null overlay that clears existing identifier meta; any other
   * non-{@link Map} value fails at the occurrence's identifier-meta location.
   */
  private @Nullable Meta metaFromConverted(
      @Nullable Object converted, Object resource, MappingLocation metaLocation) {
    if (converted == null) {
      return null;
    }
    if (!(converted instanceof Map<?, ?> map)) {
      throw metaValueFailure(
          resource,
          metaLocation,
          "Converted identifier meta value is not an object (expected a JSON object, got "
              + convertedTypeName(converted)
              + ")",
          null);
    }
    try {
      return Meta.of(castMembers(map, resource, metaLocation));
    } catch (JsonApiValidationException e) {
      throw metaValueFailure(resource, metaLocation, "Invalid identifier meta members", e);
    }
  }

  private static String convertedTypeName(@Nullable Object converted) {
    return converted == null ? "null" : converted.getClass().getName();
  }

  /**
   * Rebuilds the converted meta value into a string-keyed member map. Configured whole-meta
   * conversion targets {@code Object.class}, which normally yields string keys, so the non-string
   * branch keeps the stable {@link MappingDiagnostic#INVALID_META_TARGET} diagnostic at the known
   * {@code metaLocation} instead of leaking a class cast or a core-validation failure when
   * conversion produces non-string keys.
   */
  private static Map<String, Object> castMembers(
      Map<?, ?> map, Object resource, MappingLocation metaLocation) {
    Map<String, Object> members = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      Object key = entry.getKey();
      if (!(key instanceof String stringKey)) {
        throw new JsonApiMappingException(
            MappingDiagnostic.INVALID_META_TARGET,
            resource.getClass(),
            metaLocation,
            "Meta object key is not a string: " + key);
      }
      members.put(stringKey, entry.getValue());
    }
    return members;
  }

  private static JsonApiMappingException metaValueFailure(
      Object resource, MappingLocation metaLocation, String message, @Nullable Throwable cause) {
    return cause == null
        ? new JsonApiMappingException(
            MappingDiagnostic.INVALID_META_TARGET, resource.getClass(), metaLocation, message)
        : new JsonApiMappingException(
            MappingDiagnostic.INVALID_META_TARGET,
            resource.getClass(),
            metaLocation,
            message,
            cause);
  }

  /** Extracted identity-role values; either may be null when its member is absent. */
  private record IdentityValues(@Nullable String id, @Nullable String localId) {}
}
