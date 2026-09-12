package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kazforge.jsonapi.core.model.Attributes;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.Relationships;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.core.validation.JsonApiValidationException;
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.jackson.diagnostic.MappingLocation;
import com.kazforge.jsonapi.jackson.internal.mapping.IdentifierMetaSupport;
import com.kazforge.jsonapi.jackson.internal.mapping.PropertyRole;
import com.kazforge.jsonapi.jackson.internal.representation.EffectiveRepresentation;
import com.kazforge.jsonapi.jackson.mapping.IdentifierConverter;
import com.kazforge.jsonapi.jackson.mapping.RelationshipDecoration;
import com.kazforge.jsonapi.jackson.mapping.RelationshipLinkage;
import com.kazforge.jsonapi.jackson.mapping.ResourceDecoration;
import com.kazforge.jsonapi.jackson.mapping.ResourceDecorator;
import com.kazforge.jsonapi.jackson.mapping.ResourceDecoratorRegistry;
import com.kazforge.jsonapi.jackson.representation.FieldPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class DomainResourceWriter {

  private static final String RESOURCE = "resource";
  private static final String DECLARED_TYPE = "declaredType";
  private static final String REPRESENTATION = "representation";

  private final IdentifierConverter identifierConverter;
  private final MappingDefinitionCache cache;
  private final WholeMetaTarget wholeMetaTarget;
  private final PropertyScopedValueConverter propertyScoped;
  private final ResourceDecoratorRegistry decoratorRegistry;

  @SuppressWarnings("unused")
  public DomainResourceWriter(
      JsonMapper mapper, IdentifierConverter identifierConverter, MappingDefinitionCache cache) {
    this(mapper, identifierConverter, cache, ResourceDecoratorRegistry.empty());
  }

  public DomainResourceWriter(
      JsonMapper mapper,
      IdentifierConverter identifierConverter,
      MappingDefinitionCache cache,
      ResourceDecoratorRegistry decoratorRegistry) {
    this.identifierConverter = Objects.requireNonNull(identifierConverter, "identifierConverter");
    this.cache = Objects.requireNonNull(cache, "cache");
    this.decoratorRegistry = Objects.requireNonNull(decoratorRegistry, "decoratorRegistry");
    this.wholeMetaTarget = new WholeMetaTarget(mapper);
    this.propertyScoped = new PropertyScopedValueConverter(mapper);
  }

  public JavaType inferredType(Object resource) {
    Objects.requireNonNull(resource, RESOURCE);
    return cache.constructType(resource.getClass());
  }

  public JavaType effectiveType(Object resource, JavaType declaredType) {
    Objects.requireNonNull(resource, RESOURCE);
    Objects.requireNonNull(declaredType, DECLARED_TYPE);
    if (declaredType.getRawClass() == resource.getClass()) {
      return declaredType;
    }
    return cache.specializeType(declaredType, resource.getClass());
  }

  public ResourceObject toResource(Object resource, JavaType declaredType) {
    Objects.requireNonNull(resource, RESOURCE);
    Objects.requireNonNull(declaredType, DECLARED_TYPE);
    requireAssignable(resource, declaredType);
    ResourceMapping mapping = mappingFor(declaredType);
    validateMetaTargets(mapping, resource.getClass());
    IdentityValues identity = extractIdentity(resource, mapping);
    Attributes attributes = buildAttributes(resource, mapping, null);
    Relationships relationships = buildRelationships(resource, mapping, null);
    Meta meta = buildResourceMeta(resource, mapping);
    ResourceObject base =
        buildResourceObject(
            mapping, identity.id(), identity.localId(), attributes, relationships, meta);
    return decorateResource(resource, declaredType, mapping, base, null);
  }

  /**
   * Selective emission using fieldsets and {@link FieldPolicy} from {@code representation}.
   * Validates a present fieldset entry for the resource's mapped type before any selective
   * attribute or relationship reads.
   */
  public ResourceObject toResource(
      Object resource, JavaType declaredType, EffectiveRepresentation representation) {
    Objects.requireNonNull(resource, RESOURCE);
    Objects.requireNonNull(declaredType, DECLARED_TYPE);
    Objects.requireNonNull(representation, REPRESENTATION);
    requireAssignable(resource, declaredType);
    ResourceMapping mapping = mappingFor(declaredType);
    List<String> fields = fieldsFor(representation, mapping.resourceType());
    if (fields != null) {
      validateFieldset(resource.getClass(), mapping, fields, representation.policy().fieldPolicy());
    }
    return toResourceSelective(resource, declaredType, mapping, fields);
  }

  /**
   * Selective create-request emission mirroring {@link #toResourceSelective} except for primary
   * identity: when both identity roles yield nothing, the resource maps with absent {@code id} and
   * {@code lid} instead of failing, leaving that leniency to core {@code CREATE_REQUEST}
   * validation. Related linkage extraction and included resources stay strict.
   */
  public ResourceObject toCreateResource(
      Object resource, JavaType declaredType, EffectiveRepresentation representation) {
    Objects.requireNonNull(resource, RESOURCE);
    Objects.requireNonNull(declaredType, DECLARED_TYPE);
    Objects.requireNonNull(representation, REPRESENTATION);
    requireAssignable(resource, declaredType);
    ResourceMapping mapping = mappingFor(declaredType);
    List<String> fields = fieldsFor(representation, mapping.resourceType());
    if (fields != null) {
      validateFieldset(resource.getClass(), mapping, fields, representation.policy().fieldPolicy());
    }
    return toResourceSelective(resource, declaredType, mapping, fields, true);
  }

  private ResourceObject toResourceSelective(
      Object resource,
      JavaType declaredType,
      ResourceMapping mapping,
      @Nullable List<String> fields) {
    return toResourceSelective(resource, declaredType, mapping, fields, false);
  }

  private ResourceObject toResourceSelective(
      Object resource,
      JavaType declaredType,
      ResourceMapping mapping,
      @Nullable List<String> fields,
      boolean allowAbsentIdentity) {
    validateMetaTargets(mapping, resource.getClass());
    IdentityValues identity =
        allowAbsentIdentity
            ? extractCreateIdentity(resource, mapping)
            : extractIdentity(resource, mapping);
    Set<String> allowedFields = fields == null ? null : Set.copyOf(fields);
    Attributes attributes = buildAttributes(resource, mapping, allowedFields);
    Relationships relationships = buildRelationships(resource, mapping, allowedFields);
    Meta meta = buildResourceMeta(resource, mapping);
    ResourceObject base =
        buildResourceObject(
            mapping, identity.id(), identity.localId(), attributes, relationships, meta);
    return decorateResource(resource, declaredType, mapping, base, allowedFields);
  }

  /**
   * Resolves the fieldset list for {@code resourceType}: {@code null} when the type key is absent
   * (unrestricted), otherwise the stored list (possibly empty, selecting no
   * attributes/relationships).
   */
  public static @Nullable List<String> fieldsFor(
      EffectiveRepresentation representation, String resourceType) {
    Objects.requireNonNull(representation, REPRESENTATION);
    Objects.requireNonNull(resourceType, "resourceType");
    Map<String, List<String>> fieldsets = representation.selection().fieldsets();
    if (!fieldsets.containsKey(resourceType)) {
      return null;
    }
    return fieldsets.get(resourceType);
  }

  private static void validateFieldset(
      Class<?> resourceClass,
      ResourceMapping mapping,
      List<String> fields,
      FieldPolicy fieldPolicy) {
    if (fields.isEmpty()) {
      return;
    }
    Set<String> mappedNames = new HashSet<>();
    for (MappingProperty property : mapping.attributes()) {
      mappedNames.add(property.jsonapiName());
    }
    for (MappingProperty property : mapping.relationships()) {
      mappedNames.add(property.jsonapiName());
    }
    for (String name : fields) {
      if (!mappedNames.contains(name)) {
        // Fieldset specification failures have no document member location; the offending field
        // name stays in the message per the mapping-location contract.
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.INVALID_FIELDSET_FIELD,
            resourceClass,
            "Unknown fieldset field '" + name + "' on " + mapping.resourceType());
      }
      if (!fieldPolicy.allows(mapping.resourceType(), name)) {
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.DENIED_FIELDSET_FIELD,
            resourceClass,
            "Fieldset field denied for " + mapping.resourceType() + "." + name);
      }
    }
  }

  /**
   * Emits the mapped identity roles into their own core members: the id role becomes {@link
   * ResourceObject#id()} and the local-id role becomes {@link ResourceObject#lid()}. {@code lid}
   * never substitutes for a missing {@code id} and vice versa.
   */
  private static ResourceObject buildResourceObject(
      ResourceMapping mapping,
      @Nullable String id,
      @Nullable String lid,
      Attributes attributes,
      Relationships relationships,
      @Nullable Meta meta) {
    return new ResourceObject(
        mapping.resourceType(),
        id,
        lid,
        attributes.isEmpty() ? null : attributes,
        relationships.isEmpty() ? null : relationships,
        null,
        meta,
        Map.of());
  }

  @SuppressWarnings("NullAway")
  private ResourceObject decorateResource(
      Object domain,
      JavaType declaredType,
      ResourceMapping mapping,
      ResourceObject base,
      @Nullable Set<String> allowedFields) {
    if (decoratorRegistry.isEmpty()) {
      return base;
    }
    JavaType effectiveType = effectiveType(domain, declaredType);
    @SuppressWarnings("unchecked")
    ResourceDecorator<Object> decorator =
        (ResourceDecorator<Object>) decoratorRegistry.decoratorFor(effectiveType.getRawClass());
    if (decorator == null) {
      return base;
    }
    ResourceDecoration decoration = requireDecoration(domain, decorator, mapping.resourceType());
    Map<String, RelationshipDecoration> decorationRelationships =
        requireDecorationRelationships(domain, decoration, mapping.resourceType());
    LinkedHashMap<String, Relationship> decoratedRelationships =
        resolveRelationshipDecorations(
            domain, mapping, base, decorationRelationships, allowedFields);
    com.kazforge.jsonapi.core.model.Links resourceLinks = decoration.links();
    boolean hasResourceLinks = resourceLinks != null;
    boolean hasRelationshipLinks = decoratedRelationships != null;
    if (!hasResourceLinks && !hasRelationshipLinks) {
      return base;
    }
    Relationships finalRelationships;
    if (hasRelationshipLinks) {
      finalRelationships = Relationships.ofRelationships(decoratedRelationships);
    } else if (base.relationships() == null) {
      finalRelationships = Relationships.empty();
    } else {
      finalRelationships = base.relationships();
    }
    return new ResourceObject(
        base.type(),
        base.id(),
        base.lid(),
        base.attributes(),
        finalRelationships.isEmpty() ? null : finalRelationships,
        hasResourceLinks ? resourceLinks : base.links(),
        base.meta(),
        base.additionalMembers());
  }

  @SuppressWarnings({"java:S2583", "ConstantValue"})
  private ResourceDecoration requireDecoration(
      Object domain, ResourceDecorator<Object> decorator, String resourceType) {
    ResourceDecoration decoration;
    try {
      decoration = decorator.decorate(domain);
    } catch (RuntimeException e) {
      throw new JsonApiMappingException(
          MappingDiagnostic.INVALID_DECORATION_STATE,
          domain.getClass(),
          null,
          "Decorator failed for " + resourceType + ": " + e.getMessage(),
          e);
    }
    if (decoration == null) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.INVALID_DECORATION_STATE,
          domain.getClass(),
          "Decorator returned null for " + resourceType);
    }
    return decoration;
  }

  @SuppressWarnings("java:S2583")
  private Map<String, RelationshipDecoration> requireDecorationRelationships(
      Object domain, ResourceDecoration decoration, String resourceType) {
    Map<String, RelationshipDecoration> decorationRelationships;
    try {
      decorationRelationships = decoration.relationships();
    } catch (RuntimeException e) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.INVALID_DECORATION_STATE,
          domain.getClass(),
          "Invalid decoration relationships for " + resourceType);
    }
    // ResourceDecoration is final with a validated private constructor, so relationships() cannot
    // return null; no defensive null branch is needed here.
    return decorationRelationships;
  }

  @SuppressWarnings("NullAway")
  private @Nullable LinkedHashMap<String, Relationship> resolveRelationshipDecorations(
      Object domain,
      ResourceMapping mapping,
      ResourceObject base,
      Map<String, RelationshipDecoration> decorationRelationships,
      @Nullable Set<String> allowedFields) {
    if (decorationRelationships.isEmpty()) {
      return null;
    }
    Map<String, MappingProperty> byLogical = indexRelationships(mapping);
    Map<String, String> nonRelationshipKind = indexNonRelationships(mapping);
    Map<String, Relationship> baseRelationships =
        base.relationships() == null ? Map.of() : base.relationships().relationships();
    LinkedHashMap<String, Relationship> decoratedRelationships = null;
    for (Map.Entry<String, RelationshipDecoration> entry : decorationRelationships.entrySet()) {
      String logicalName = entry.getKey();
      RelationshipDecoration relationshipDecoration = entry.getValue();
      validateDecorationEntry(domain, logicalName, relationshipDecoration);
      MappingProperty target = byLogical.get(logicalName);
      if (target == null) {
        throwInvalidTarget(domain, mapping.resourceType(), logicalName, nonRelationshipKind);
        continue;
      }
      String wireName = Objects.requireNonNull(target, "target").jsonapiName();
      Relationship existing = baseRelationships.get(wireName);
      com.kazforge.jsonapi.core.model.Links decorationLinks = relationshipDecoration.links();
      boolean shouldDecorate =
          (allowedFields == null || allowedFields.contains(wireName))
              && existing != null
              && decorationLinks != null;
      if (shouldDecorate) {
        if (decoratedRelationships == null) {
          decoratedRelationships = new LinkedHashMap<>(baseRelationships);
        }
        Relationship nonNullExisting = Objects.requireNonNull(existing, "existing");
        Relationship decorated =
            new Relationship(
                nonNullExisting.data(),
                decorationLinks,
                nonNullExisting.meta(),
                nonNullExisting.additionalMembers());
        decoratedRelationships.put(wireName, decorated);
      }
    }
    return decoratedRelationships;
  }

  private Map<String, MappingProperty> indexRelationships(ResourceMapping mapping) {
    Map<String, MappingProperty> byLogical = new LinkedHashMap<>();
    for (MappingProperty property : mapping.relationships()) {
      byLogical.put(property.logicalName(), property);
    }
    return byLogical;
  }

  private Map<String, String> indexNonRelationships(ResourceMapping mapping) {
    Map<String, String> nonRelationshipKind = new LinkedHashMap<>();
    MappingProperty identifierProperty = mapping.identifierProperty();
    if (identifierProperty != null) {
      nonRelationshipKind.put(identifierProperty.logicalName(), "identifier");
    }
    MappingProperty localIdProperty = mapping.localIdProperty();
    if (localIdProperty != null) {
      nonRelationshipKind.put(localIdProperty.logicalName(), "identifier");
    }
    for (MappingProperty property : mapping.attributes()) {
      nonRelationshipKind.put(property.logicalName(), "attribute");
    }
    MappingProperty resourceMeta = mapping.resourceMeta();
    if (resourceMeta != null) {
      nonRelationshipKind.put(resourceMeta.logicalName(), "resource meta");
    }
    for (MappingProperty property : mapping.relationshipMetaProperties()) {
      nonRelationshipKind.put(property.logicalName(), "relationship meta");
    }
    return nonRelationshipKind;
  }

  @SuppressWarnings("java:S2583")
  private void validateDecorationEntry(
      Object domain,
      @Nullable String logicalName,
      @Nullable RelationshipDecoration relationshipDecoration) {
    if (logicalName == null) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.INVALID_DECORATION_STATE,
          domain.getClass(),
          "Decoration contains null relationship property");
    }
    if (logicalName.isEmpty()) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.INVALID_DECORATION_STATE,
          domain.getClass(),
          "Decoration contains empty relationship property");
    }
    if (relationshipDecoration == null) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.INVALID_DECORATION_STATE,
          domain.getClass(),
          "Decoration for relationship '" + logicalName + "' is null");
    }
  }

  private void throwInvalidTarget(
      Object domain,
      String resourceType,
      String logicalName,
      Map<String, String> nonRelationshipKind) {
    String kind = nonRelationshipKind.get(logicalName);
    if (kind != null) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.INVALID_DECORATION_TARGET,
          domain.getClass(),
          "Decoration target '"
              + logicalName
              + "' is a "
              + kind
              + ", not a relationship on "
              + resourceType);
    }
    throw JsonApiMappingException.withoutLocation(
        MappingDiagnostic.INVALID_DECORATION_TARGET,
        domain.getClass(),
        "Unknown decoration target '" + logicalName + "' on " + resourceType);
  }

  /**
   * Whole-meta declared-target validation for the write mapping role: Bean / Map / Object with at
   * most one {@link Optional} wrapper. Validation lives at the consuming entry point, not the
   * kind-agnostic resolver (ADR-015).
   */
  private void validateMetaTargets(ResourceMapping mapping, Class<?> rawType) {
    wholeMetaTarget.validateReadWriteTargets(mapping, rawType);
  }

  /**
   * Reads the mapped id and local-id roles independently. A null Java value on either role means
   * that member is absent; only when both roles yield nothing does the resource lack identity and
   * fail with {@link MappingDiagnostic#MISSING_IDENTIFIER}. A present value that fails conversion
   * fails at its own role's wire location.
   */
  private IdentityValues extractIdentity(Object resource, ResourceMapping mapping) {
    String id = extractId(resource, mapping);
    String localId = extractLocalId(resource, mapping);
    if (id == null && localId == null) {
      MappingProperty idProperty = mapping.identifierProperty();
      if (idProperty != null) {
        throw missingIdentifier(
            resource.getClass(),
            MappingLocation.of("id"),
            nullIdentityMessage("Identifier", idProperty.logicalName()));
      }
      MappingProperty localIdProperty = Objects.requireNonNull(mapping.localIdProperty());
      throw missingIdentifier(
          resource.getClass(),
          MappingLocation.of("lid"),
          nullIdentityMessage("Local-id", localIdProperty.logicalName()));
    }
    return new IdentityValues(id, localId);
  }

  /**
   * Reads the mapped id and local-id roles for create-request authoring. Unlike {@link
   * #extractIdentity}, a resource with neither role value maps with absent identity instead of
   * failing; core {@code CREATE_REQUEST} validation owns that leniency. Present values convert and
   * fail exactly as on the ordinary path.
   */
  private IdentityValues extractCreateIdentity(Object resource, ResourceMapping mapping) {
    String id = extractId(resource, mapping);
    String localId = extractLocalId(resource, mapping);
    return new IdentityValues(id, localId);
  }

  private static String nullIdentityMessage(String roleLabel, String logicalName) {
    return roleLabel + " property '" + logicalName + "' is null";
  }

  @Nullable String extractId(Object resource, ResourceMapping mapping) {
    MappingProperty identifierProperty = mapping.identifierProperty();
    if (identifierProperty == null) {
      return null;
    }
    Object identifierValue =
        unwrapOptional(readValue(resource, identifierProperty, PropertyRole.ID));
    if (identifierValue == null) {
      return null;
    }
    return requireIdentifierString(resource.getClass(), identifierProperty, identifierValue);
  }

  @Nullable String extractLocalId(Object resource, ResourceMapping mapping) {
    MappingProperty localIdProperty = mapping.localIdProperty();
    if (localIdProperty == null) {
      return null;
    }
    Object localIdValue =
        unwrapOptional(readValue(resource, localIdProperty, PropertyRole.LOCAL_ID));
    if (localIdValue == null) {
      return null;
    }
    return requireLocalIdString(resource.getClass(), localIdProperty, localIdValue);
  }

  private String requireIdentifierString(
      Class<?> type, MappingProperty property, @Nullable Object identifierValue) {
    // Callers guarantee a present value; null identity state is decided by extractIdentity.
    String identifierString = identifierConverter.convert(identifierValue);
    if (identifierString == null) {
      throw missingIdentifier(
          type,
          MappingLocation.of("id"),
          "Identifier converter returned null for property '" + property.logicalName() + "'");
    }
    return identifierString;
  }

  private String requireLocalIdString(
      Class<?> type, MappingProperty property, @Nullable Object localIdValue) {
    // Callers guarantee a present value; null identity state is decided by extractIdentity.
    String localIdString = identifierConverter.convert(localIdValue);
    if (localIdString == null) {
      throw missingIdentifier(
          type,
          MappingLocation.of("lid"),
          "Local-id converter returned null for property '" + property.logicalName() + "'");
    }
    return localIdString;
  }

  public ResourceIdentifier extractIdentifier(Object resource, JavaType declaredType) {
    Objects.requireNonNull(resource, RESOURCE);
    Objects.requireNonNull(declaredType, DECLARED_TYPE);
    requireAssignable(resource, declaredType);
    ResourceMapping mapping = mappingFor(declaredType);
    IdentityValues identity = extractIdentity(resource, mapping);
    return new ResourceIdentifier(
        mapping.resourceType(), identity.id(), identity.localId(), null, Map.of());
  }

  /** Resolves the cached mapping definition for a complete declared type. */
  ResourceMapping mappingFor(JavaType declaredType) {
    MappingDefinitionCache.ValidatedMapping validated = cache.resolveValidated(declaredType);
    Optional<MappingProperty> unresolvedProperty = validated.unresolvedProperty();
    if (unresolvedProperty.isPresent()) {
      MappingProperty unresolved = unresolvedProperty.orElseThrow();
      throw new JsonApiMappingException(
          MappingDiagnostic.UNRESOLVED_GENERIC_TYPE,
          declaredType.getRawClass(),
          ResolvedTypeSupport.location(unresolved),
          ResolvedTypeSupport.message(unresolved, declaredType));
    }
    return validated.mapping();
  }

  /** Reads a relationship property for inclusion traversal (not linkage construction). */
  @Nullable Object readRelationshipValue(Object resource, MappingProperty property) {
    return readValue(resource, property, PropertyRole.RELATIONSHIP);
  }

  private static void requireAssignable(Object resource, JavaType declaredType) {
    if (!declaredType.getRawClass().isInstance(resource)) {
      throw new IllegalArgumentException(
          "Resource of type "
              + resource.getClass().getName()
              + " is not assignable to declared type "
              + declaredType.toCanonical());
    }
  }

  static boolean isToManyType(JavaType type) {
    if (type.isArrayType()) {
      return true;
    }
    if (type.isCollectionLikeType()) {
      return true;
    }
    return type.isTypeOrSubTypeOf(Iterable.class);
  }

  static @Nullable JavaType resolveContentType(JavaType type) {
    if (type.isArrayType() || type.isCollectionLikeType()) {
      return type.getContentType();
    }
    if (type.containedTypeCount() > 0) {
      return type.containedType(0);
    }
    return null;
  }

  static @Nullable Object unwrapOptional(@Nullable Object value) {
    if (value instanceof Optional<?> optional) {
      return optional.orElse(null);
    }
    return value;
  }

  /**
   * Converts an already-read to-many value into a list of elements. Serialization callers supply
   * the failing relationship's resource-relative location; inclusion traversal, which has no
   * JSON:API member coordinate for this value, uses {@link #convertToCollection(Object)}.
   */
  static List<Object> convertToCollection(
      Object value, @Nullable MappingLocation relationshipLocation) {
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
      default -> throw relationshipShapeFailure(value, relationshipLocation);
    };
  }

  /**
   * Locationless variant for callers without a JSON:API member coordinate (inclusion traversal).
   */
  static List<Object> convertToCollection(Object value) {
    return convertToCollection(value, null);
  }

  private static JsonApiMappingException relationshipShapeFailure(
      Object value, @Nullable MappingLocation relationshipLocation) {
    String message =
        "To-many relationship value is not a supported collection type: "
            + value.getClass().getName();
    return relationshipLocation == null
        ? JsonApiMappingException.withoutLocation(
            MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_VALUE, value.getClass(), message)
        : new JsonApiMappingException(
            MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_VALUE,
            value.getClass(),
            relationshipLocation,
            message);
  }

  private Attributes buildAttributes(
      Object resource, ResourceMapping mapping, @Nullable Set<String> allowedFields) {
    if (mapping.attributes().isEmpty()) {
      return Attributes.empty();
    }
    Map<String, @Nullable Object> attributes = new LinkedHashMap<>();
    for (MappingProperty property : mapping.attributes()) {
      if (allowedFields == null || allowedFields.contains(property.jsonapiName())) {
        Object rawValue = readValue(resource, property, PropertyRole.ATTRIBUTE);
        if (!(rawValue instanceof Optional<?> optional) || optional.isPresent()) {
          PropertyScopedValueConverter.SerializationResult converted =
              convertAttributeValue(resource, mapping, property, rawValue);
          if (converted.emitted()) {
            attributes.put(property.jsonapiName(), converted.value());
          }
        }
      }
    }
    return Attributes.ofAttributes(attributes);
  }

  private Relationships buildRelationships(
      Object resource, ResourceMapping mapping, @Nullable Set<String> allowedFields) {
    if (mapping.relationships().isEmpty()) {
      return Relationships.empty();
    }
    Map<String, MappingProperty> relationshipMetaByTarget =
        RelationshipMetaSupport.byTarget(mapping.relationshipMetaProperties());
    Map<String, @Nullable Relationship> relationships = new LinkedHashMap<>();
    for (MappingProperty property : mapping.relationships()) {
      if (allowedFields != null && !allowedFields.contains(property.jsonapiName())) {
        continue;
      }
      relationships.put(
          property.jsonapiName(),
          buildRelationship(resource, mapping, property, relationshipMetaByTarget));
    }
    return Relationships.ofRelationships(relationships);
  }

  private Relationship buildRelationship(
      Object resource,
      ResourceMapping mapping,
      MappingProperty property,
      Map<String, MappingProperty> relationshipMetaByTarget) {
    Object value = readValue(resource, property, PropertyRole.RELATIONSHIP);
    JavaType propertyType = property.accessor().getType();
    MappingLocation relationshipLocation =
        RelationshipLinkageSupport.relationshipLocation(property);
    boolean toMany = isToManyType(propertyType);
    RelationshipData linkage =
        toMany
            ? extractToManyLinkage(resource, property, value, propertyType, relationshipLocation)
            : extractToOneLinkage(resource, property, value, propertyType);
    Meta meta = null;
    MappingProperty metaProperty = relationshipMetaByTarget.get(property.jsonapiName());
    if (metaProperty != null) {
      meta =
          buildMetaValue(
              resource,
              mapping,
              metaProperty,
              RelationshipMetaSupport.relationshipMetaLocation(property.jsonapiName()));
    }
    return new Relationship(linkage, null, meta, Map.of());
  }

  /** Builds the resource-side {@code meta} from the single mapped resource-meta property. */
  private @Nullable Meta buildResourceMeta(Object resource, ResourceMapping mapping) {
    MappingProperty resourceMetaProperty = mapping.resourceMeta();
    if (resourceMetaProperty == null) {
      return null;
    }
    return buildMetaValue(
        resource, mapping, resourceMetaProperty, RelationshipMetaSupport.resourceMetaLocation());
  }

  /**
   * Converts one whole-meta property value into a core {@link Meta}. The converted result must be a
   * {@link Map}; scalar/array/non-object runtime values fail with a stable meta diagnostic (never a
   * leaked cast or core-validation failure). Failures report the location-specific {@code path}.
   */
  private @Nullable Meta buildMetaValue(
      Object resource,
      ResourceMapping mapping,
      MappingProperty property,
      MappingLocation metaLocation) {
    Object rawValue = readValue(resource, property, property.role());
    Object value = unwrapOptional(rawValue);
    if (value == null) {
      return null;
    }
    Object converted;
    try {
      PropertyScopedValueConverter.SerializationResult serialized =
          propertyScoped.serialize(
              mapping.domainType(),
              property.definition().getFullName().getSimpleName(),
              resource,
              rawValue,
              value);
      if (!serialized.emitted()) {
        return null;
      }
      converted = serialized.value();
    } catch (RuntimeException e) {
      throw metaValueFailure(resource, metaLocation, "Failed to convert meta value", e);
    }
    if (!(converted instanceof Map<?, ?> map)) {
      throw metaValueFailure(
          resource,
          metaLocation,
          "Converted meta value is not an object (expected a JSON object, got "
              + convertedTypeName(converted)
              + ")",
          null);
    }
    try {
      return Meta.of(castMembers(map, resource, metaLocation));
    } catch (JsonApiValidationException e) {
      throw metaValueFailure(resource, metaLocation, "Invalid meta members", e);
    }
  }

  private static String convertedTypeName(@Nullable Object converted) {
    return converted == null ? "null" : converted.getClass().getName();
  }

  /**
   * Rebuilds the converted meta value into a string-keyed member map. Today the conversion target
   * {@code Object.class} always yields string keys (Jackson's untyped map representation), so the
   * non-string branch is defensive: it keeps the stable {@link
   * MappingDiagnostic#INVALID_META_TARGET} diagnostic at the known {@code metaLocation} instead of
   * leaking a class cast or a core-validation failure if a future Jackson version ever emits
   * non-string keys.
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

  private JsonApiMappingException metaValueFailure(
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

  /**
   * Overlay wrapper {@code meta} onto constructed linkage. {@code meta == null} supplies no new
   * identifier meta and leaves any {@link ResourceIdentifier#meta()} already on the target in
   * place. A non-null meta value is authoritative. Unrelated identifier members are preserved.
   */
  private RelationshipData applyWrapperMeta(
      Object resource,
      JavaType metaType,
      @Nullable Object metaValue,
      RelationshipData linkage,
      String relationshipName,
      int index) {
    MappingLocation metaLocation =
        index < 0
            ? IdentifierMetaSupport.identifierMetaLocation(relationshipName)
            : IdentifierMetaSupport.identifierMetaLocation(relationshipName, index);
    if (linkage instanceof RelationshipData.NullLinkage) {
      throw new JsonApiMappingException(
          MappingDiagnostic.INVALID_IDENTIFIER_META_TARGET,
          resource.getClass(),
          metaLocation,
          "RelationshipLinkage requires a mappable target for relationship '"
              + relationshipName
              + "'");
    }
    if (metaValue == null) {
      return linkage;
    }
    Object converted;
    try {
      PropertyScopedValueConverter.SerializationResult serialized =
          propertyScoped.serializeDeclared(metaType, metaValue);
      if (!serialized.emitted()) {
        return linkage;
      }
      converted = serialized.value();
    } catch (RuntimeException e) {
      throw new JsonApiMappingException(
          MappingDiagnostic.INVALID_META_TARGET,
          resource.getClass(),
          metaLocation,
          "Failed to convert identifier meta for relationship '" + relationshipName + "'",
          e);
    }
    Meta meta = metaFromConverted(converted, resource, metaLocation);
    if (linkage instanceof RelationshipData.SingleLinkage(ResourceIdentifier identifier)) {
      return new RelationshipData.SingleLinkage(IdentifierMetaSupport.withMeta(identifier, meta));
    }
    throw new JsonApiMappingException(
        MappingDiagnostic.INVALID_IDENTIFIER_META_TARGET,
        resource.getClass(),
        metaLocation,
        "Identifier meta requires to-one linkage for relationship '" + relationshipName + "'");
  }

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

  private RelationshipData extractToOneLinkage(
      Object resource, MappingProperty property, @Nullable Object value, JavaType propertyType) {
    value = unwrapOptional(value);
    JavaType linkageType = RelationshipLinkageSupport.linkageJavaType(propertyType);
    if (linkageType != null) {
      if (value == null) {
        return RelationshipData.NullLinkage.INSTANCE;
      }
      if (!(value instanceof RelationshipLinkage<?, ?>(Object target, Object meta))) {
        throw new JsonApiMappingException(
            MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_VALUE,
            value.getClass(),
            RelationshipLinkageSupport.relationshipLocation(property),
            "Relationship '"
                + property.logicalName()
                + "' requires RelationshipLinkage values, got "
                + value.getClass().getName());
      }
      RelationshipData data =
          extractToOneLinkage(
              resource,
              property,
              target,
              RelationshipLinkageSupport.linkageTargetType(linkageType));
      return applyWrapperMeta(
          resource,
          RelationshipLinkageSupport.linkageMetaType(linkageType),
          meta,
          data,
          property.jsonapiName(),
          -1);
    }
    return switch (value) {
      case null -> RelationshipData.NullLinkage.INSTANCE;
      case ResourceIdentifier resourceIdentifier ->
          new RelationshipData.SingleLinkage(resourceIdentifier);
      case RelationshipData relationshipData -> relationshipData;
      default ->
          new RelationshipData.SingleLinkage(
              extractIdentifier(
                  Objects.requireNonNull(value), relationshipTargetType(value, propertyType)));
    };
  }

  private JavaType relationshipTargetType(Object value, JavaType propertyType) {
    JavaType unwrapped = RelationshipLinkageSupport.unwrapOptionalType(propertyType);
    if (unwrapped.getRawClass() == Object.class
        || (unwrapped.getRawClass() == Optional.class && unwrapped.containedTypeCount() == 0)) {
      return inferredType(value);
    }
    return effectiveType(value, unwrapped);
  }

  private RelationshipData extractToManyLinkage(
      Object resource,
      MappingProperty property,
      @Nullable Object value,
      JavaType propType,
      MappingLocation relationshipLocation) {
    if (value == null) {
      return RelationshipData.IdentifierCollectionLinkage.empty();
    }
    JavaType linkageType = RelationshipLinkageSupport.linkageJavaType(propType);
    if (linkageType != null) {
      List<Object> items = convertToCollection(value, relationshipLocation);
      return toManyWrappedLinkage(resource, property, items, linkageType, relationshipLocation);
    }
    List<Object> items = convertToCollection(value, relationshipLocation);
    if (items.isEmpty()) {
      return RelationshipData.IdentifierCollectionLinkage.empty();
    }
    return switch (classifyToManyItems(items)) {
      case ResourceIdentifiers(List<ResourceIdentifier> identifiers) ->
          new RelationshipData.IdentifierCollectionLinkage(identifiers);
      case DomainObjects(List<?> domainItems) ->
          toManyLinkageFromDomainObjects(domainItems, propType, relationshipLocation);
      case Mixed(Object firstNonResourceIdentifier) ->
          throw mixedToManyElements(firstNonResourceIdentifier, relationshipLocation);
    };
  }

  private RelationshipData toManyWrappedLinkage(
      Object resource,
      MappingProperty property,
      List<Object> items,
      JavaType linkageType,
      MappingLocation relationshipLocation) {
    if (items.isEmpty()) {
      return RelationshipData.IdentifierCollectionLinkage.empty();
    }
    JavaType targetType = RelationshipLinkageSupport.linkageTargetType(linkageType);
    JavaType metaType = RelationshipLinkageSupport.linkageMetaType(linkageType);
    List<ResourceIdentifier> identifiers = new ArrayList<>();
    int index = 0;
    for (Object item : items) {
      Object unwrappedItem = unwrapOptional(item);
      if (unwrappedItem == null) {
        continue;
      }
      if (!(unwrappedItem instanceof RelationshipLinkage<?, ?>(Object target, Object meta))) {
        throw new JsonApiMappingException(
            MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_VALUE,
            unwrappedItem.getClass(),
            relationshipLocation,
            "To-many RelationshipLinkage collection contains "
                + unwrappedItem.getClass().getName());
      }
      RelationshipData data = extractToOneLinkage(resource, property, target, targetType);
      RelationshipData overlaid =
          applyWrapperMeta(resource, metaType, meta, data, property.jsonapiName(), index);
      if (overlaid instanceof RelationshipData.SingleLinkage(ResourceIdentifier identifier)) {
        identifiers.add(identifier);
        index++;
      }
    }
    return new RelationshipData.IdentifierCollectionLinkage(identifiers);
  }

  private static ToManyClassification classifyToManyItems(List<?> items) {
    boolean hasResourceIdentifier = false;
    Object firstNonResourceIdentifier = null;
    List<ResourceIdentifier> identifiers = new ArrayList<>();
    List<Object> domainItems = new ArrayList<>();
    for (Object item : items) {
      if (item == null) {
        continue;
      }
      if (item instanceof ResourceIdentifier resourceIdentifier) {
        hasResourceIdentifier = true;
        identifiers.add(resourceIdentifier);
      } else {
        if (firstNonResourceIdentifier == null) {
          firstNonResourceIdentifier = item;
        }
        domainItems.add(item);
      }
    }
    if (hasResourceIdentifier && firstNonResourceIdentifier != null) {
      return new Mixed(firstNonResourceIdentifier);
    }
    if (hasResourceIdentifier) {
      return new ResourceIdentifiers(identifiers);
    }
    return new DomainObjects(domainItems);
  }

  private RelationshipData toManyLinkageFromDomainObjects(
      List<?> items, JavaType propType, MappingLocation relationshipLocation) {
    JavaType contentType = resolveContentType(propType);
    if (contentType == null) {
      throw new JsonApiMappingException(
          MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_COLLECTION_TYPE,
          null,
          relationshipLocation,
          "Cannot resolve collection content type");
    }
    checkDeclaredTargetHasResourceMetadata(contentType, relationshipLocation);
    List<ResourceIdentifier> identifiers = new ArrayList<>(items.size());
    for (Object item : items) {
      Object nonNullItem = Objects.requireNonNull(item);
      JavaType effectiveContentType = effectiveType(nonNullItem, contentType);
      identifiers.add(extractIdentifier(nonNullItem, effectiveContentType));
    }
    return new RelationshipData.IdentifierCollectionLinkage(identifiers);
  }

  private static JsonApiMappingException missingIdentifier(
      Class<?> type, MappingLocation location, String message) {
    return new JsonApiMappingException(
        MappingDiagnostic.MISSING_IDENTIFIER, type, location, message);
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

  private static @Nullable Object readValue(
      Object resource, MappingProperty property, PropertyRole role) {
    try {
      return property.accessor().getValue(resource);
    } catch (JsonApiMappingException e) {
      throw e;
    } catch (Exception e) {
      throw new JsonApiMappingException(
          diagnosticFor(role),
          resource.getClass(),
          memberLocation(property, role),
          "Failed to read property '"
              + property.logicalName()
              + "' ("
              + role.name().toLowerCase()
              + ")",
          e);
    }
  }

  /**
   * Resource-relative wire location of one mapped member, per the mapping-location contract: the
   * JSON:API member name is escaped as pointer segments, never the Jackson logical name.
   */
  private static MappingLocation memberLocation(MappingProperty property, PropertyRole role) {
    return switch (role) {
      case ID -> MappingLocation.of("id");
      case LOCAL_ID -> MappingLocation.of("lid");
      case ATTRIBUTE -> MappingLocation.of("attributes", property.jsonapiName());
      case RELATIONSHIP -> MappingLocation.of("relationships", property.jsonapiName(), "data");
      case RESOURCE_META -> RelationshipMetaSupport.resourceMetaLocation();
      case RELATIONSHIP_META ->
          RelationshipMetaSupport.relationshipMetaLocation(property.jsonapiName());
    };
  }

  private static MappingDiagnostic diagnosticFor(PropertyRole role) {
    return switch (role) {
      case ID, LOCAL_ID -> MappingDiagnostic.MISSING_IDENTIFIER;
      case ATTRIBUTE -> MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE;
      case RELATIONSHIP -> MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_VALUE;
      case RESOURCE_META, RELATIONSHIP_META -> MappingDiagnostic.INVALID_META_TARGET;
    };
  }

  private PropertyScopedValueConverter.SerializationResult convertAttributeValue(
      Object resource,
      ResourceMapping mapping,
      MappingProperty property,
      @Nullable Object rawValue) {
    try {
      return propertyScoped.serialize(
          mapping.domainType(),
          property.definition().getFullName().getSimpleName(),
          resource,
          rawValue,
          unwrapOptional(rawValue));
    } catch (RuntimeException e) {
      throw new JsonApiMappingException(
          MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE,
          resource.getClass(),
          memberLocation(property, PropertyRole.ATTRIBUTE),
          "Failed to serialize attribute '" + property.logicalName() + "'",
          e);
    }
  }

  /**
   * Declared to-many target validation through the canonical configured-Jackson metadata authority:
   * the declared element type must carry resource metadata as the configured mapper sees it
   * (including class-level mix-ins). Presence-only, so absence keeps this path's stable diagnostic.
   */
  private void checkDeclaredTargetHasResourceMetadata(
      JavaType targetType, MappingLocation relationshipLocation) {
    if (cache.findResourceTypeName(targetType) == null) {
      throw new JsonApiMappingException(
          MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_COLLECTION_TYPE,
          targetType.getRawClass(),
          relationshipLocation,
          "Collection element type " + targetType.toCanonical() + " lacks @JsonApiResource");
    }
  }

  private sealed interface ToManyClassification permits ResourceIdentifiers, DomainObjects, Mixed {}

  /** Extracted identity-role values; either may be null when its member is absent. */
  private record IdentityValues(@Nullable String id, @Nullable String localId) {}

  private record ResourceIdentifiers(List<ResourceIdentifier> identifiers)
      implements ToManyClassification {}

  private record DomainObjects(List<?> items) implements ToManyClassification {}

  private record Mixed(Object firstNonResourceIdentifier) implements ToManyClassification {}
}
