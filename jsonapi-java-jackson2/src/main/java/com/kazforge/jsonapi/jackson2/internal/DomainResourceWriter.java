package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.Relationships;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.mapping.IdentifierConverter;
import com.kazforge.jsonapi.mapping.RelationshipDecoration;
import com.kazforge.jsonapi.mapping.ResourceDecoration;
import com.kazforge.jsonapi.mapping.ResourceDecorator;
import com.kazforge.jsonapi.mapping.ResourceDecoratorRegistry;
import com.kazforge.jsonapi.mapping.internal.BasicResourceWriter;
import com.kazforge.jsonapi.mapping.internal.EffectiveRepresentation;
import com.kazforge.jsonapi.mapping.internal.MemberConversion;
import com.kazforge.jsonapi.mapping.internal.PropertyRole;
import com.kazforge.jsonapi.mapping.internal.RelationshipShape;
import com.kazforge.jsonapi.mapping.internal.WriteProperty;
import com.kazforge.jsonapi.mapping.internal.WriteResourceBackend;
import com.kazforge.jsonapi.mapping.internal.WriteResourceDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

public final class DomainResourceWriter implements WriteResourceBackend<JavaType, MappingProperty> {

  private static final String RESOURCE = "resource";
  private static final String DECLARED_TYPE = "declaredType";
  private static final String REPRESENTATION = "representation";

  private final IdentifierConverter identifierConverter;
  private final MappingDefinitionCache cache;
  private final WholeMetaTarget wholeMetaTarget;
  private final PropertyScopedValueConverter propertyScoped;
  private final ResourceDecoratorRegistry decoratorRegistry;
  private final BasicResourceWriter<JavaType, MappingProperty> basicWriter;
  private final Map<JavaType, WriteResourceDefinition<MappingProperty>> definitions =
      new ConcurrentHashMap<>();

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
    this.basicWriter = new BasicResourceWriter<>(this);
  }

  public JavaType inferredType(Object resource) {
    Objects.requireNonNull(resource, RESOURCE);
    return cache.constructType(resource.getClass());
  }

  @Override
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
    ResourceObject base = basicWriter.writeBasic(resource, declaredType, null, false);
    return decorateResource(resource, declaredType, mapping, base, null);
  }

  /**
   * Selective emission using fieldsets and {@link com.kazforge.jsonapi.representation.FieldPolicy}
   * from {@code representation}. Validates a present fieldset entry for the resource's mapped type
   * before any selective attribute or relationship reads.
   */
  public ResourceObject toResource(
      Object resource, JavaType declaredType, EffectiveRepresentation representation) {
    Objects.requireNonNull(resource, RESOURCE);
    Objects.requireNonNull(declaredType, DECLARED_TYPE);
    Objects.requireNonNull(representation, REPRESENTATION);
    requireAssignable(resource, declaredType);
    ResourceMapping mapping = mappingFor(declaredType);
    List<String> fields = representation.fieldsFor(mapping.resourceType());
    if (fields != null) {
      basicWriter.validateFieldset(
          resource, declaredType, fields, representation.policy().fieldPolicy());
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
    List<String> fields = representation.fieldsFor(mapping.resourceType());
    if (fields != null) {
      basicWriter.validateFieldset(
          resource, declaredType, fields, representation.policy().fieldPolicy());
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
    Set<String> allowedFields = fields == null ? null : Set.copyOf(fields);
    ResourceObject base =
        basicWriter.writeBasic(resource, declaredType, allowedFields, allowAbsentIdentity);
    return decorateResource(resource, declaredType, mapping, base, allowedFields);
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
   * kind-agnostic resolver.
   */
  private void validateMetaTargets(ResourceMapping mapping, Class<?> rawType) {
    wholeMetaTarget.validateReadWriteTargets(mapping, rawType);
  }

  /**
   * Extracts the JSON:API identity of one mapped domain object through the shared strict identity
   * rule.
   */
  public ResourceIdentifier extractIdentifier(Object resource, JavaType declaredType) {
    Objects.requireNonNull(resource, RESOURCE);
    Objects.requireNonNull(declaredType, DECLARED_TYPE);
    requireAssignable(resource, declaredType);
    return basicWriter.identifier(resource, declaredType);
  }

  /** Reads the mapped id role for inclusion identity checks; not linkage construction. */
  @Nullable String extractId(Object resource, JavaType declaredType) {
    return basicWriter.extractId(resource, declaredType);
  }

  /** Reads the mapped local-id role for inclusion identity checks; not linkage construction. */
  @Nullable String extractLocalId(Object resource, JavaType declaredType) {
    return basicWriter.extractLocalId(resource, declaredType);
  }

  /** Returns whether the domain object currently carries either an id or local-id value. */
  boolean hasIdentity(Object resource, JavaType declaredType) {
    return extractId(resource, declaredType) != null
        || extractLocalId(resource, declaredType) != null;
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

  static @Nullable Object unwrapOptional(@Nullable Object value) {
    if (value instanceof Optional<?> optional) {
      return optional.orElse(null);
    }
    return value;
  }

  /**
   * Converts an already-read to-many value into a list of elements for inclusion traversal, which
   * has no JSON:API member coordinate for this value; write-side materialization is owned by the
   * shared relationship normalization operation.
   */
  static List<Object> convertToCollection(Object value) {
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
      default -> throw relationshipShapeFailure(value);
    };
  }

  private static JsonApiMappingException relationshipShapeFailure(Object value) {
    String message =
        "To-many relationship value is not a supported collection type: "
            + value.getClass().getName();
    return JsonApiMappingException.withoutLocation(
        MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_VALUE, value.getClass(), message);
  }

  /**
   * Resolves the declared element-type token for one ordinary to-many branch: the declared element
   * type must carry resource metadata as the configured mapper sees it (including class-level
   * mix-ins), and an unresolvable content type keeps the stable collection diagnostic. The shared
   * relationship writer consults this only from that lazy branch.
   */
  private JavaType resolveToManyDeclaredTarget(
      @Nullable JavaType declaredTarget, MappingLocation location) {
    if (declaredTarget == null) {
      throw new JsonApiMappingException(
          MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_COLLECTION_TYPE,
          null,
          location,
          "Cannot resolve collection content type");
    }
    checkDeclaredTargetHasResourceMetadata(declaredTarget, location);
    return declaredTarget;
  }

  /**
   * Resolves the declared target token for one ordinary to-one branch: untyped declared targets
   * fall back to the runtime type, otherwise the target specializes through configured Jackson.
   */
  private JavaType resolveToOneTarget(@Nullable Object target, @Nullable JavaType declaredTarget) {
    JavaType declared = Objects.requireNonNull(declaredTarget, "declaredTarget");
    if (declared.getRawClass() == Object.class
        || (declared.getRawClass() == Optional.class && declared.containedTypeCount() == 0)) {
      return inferredType(Objects.requireNonNull(target));
    }
    return effectiveType(Objects.requireNonNull(target), declared);
  }

  @Override
  public RelationshipShape<JavaType> relationshipShape(WriteProperty<MappingProperty> property) {
    return MappingTypeSupport.relationshipShape(property.token().accessor().getType());
  }

  @Override
  public JavaType resolveRelationshipTarget(
      @Nullable Object target,
      @Nullable JavaType declaredTarget,
      boolean relationshipToMany,
      MappingLocation relationshipLocation) {
    if (relationshipToMany) {
      return resolveToManyDeclaredTarget(declaredTarget, relationshipLocation);
    }
    return resolveToOneTarget(target, declaredTarget);
  }

  @Override
  public MemberConversion convertWholeMeta(
      Object resource,
      JavaType declaredType,
      WriteProperty<MappingProperty> property,
      @Nullable Object rawValue,
      @Nullable Object unwrappedValue) {
    MappingProperty nativeProperty = property.token();
    PropertyScopedValueConverter.SerializationResult serialized =
        propertyScoped.serialize(
            mappingFor(declaredType).domainType(),
            nativeProperty.definition().getFullName().getSimpleName(),
            resource,
            rawValue,
            unwrappedValue);
    return serialized.emitted()
        ? MemberConversion.emitted(serialized.value())
        : MemberConversion.omitted();
  }

  @Override
  public MemberConversion convertIdentifierMeta(
      JavaType declaredMetaToken, @Nullable Object metaValue) {
    PropertyScopedValueConverter.SerializationResult serialized =
        propertyScoped.serializeDeclared(declaredMetaToken, Objects.requireNonNull(metaValue));
    return serialized.emitted()
        ? MemberConversion.emitted(serialized.value())
        : MemberConversion.omitted();
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

  @Override
  public MemberConversion convertAttribute(
      Object domain, JavaType declaredType, WriteProperty<MappingProperty> property) {
    MappingProperty nativeProperty = property.token();
    Object rawValue = readValue(domain, nativeProperty, PropertyRole.ATTRIBUTE);
    if (rawValue instanceof Optional<?> optional && optional.isEmpty()) {
      return MemberConversion.omitted();
    }
    try {
      PropertyScopedValueConverter.SerializationResult converted =
          propertyScoped.serialize(
              mappingFor(declaredType).domainType(),
              nativeProperty.definition().getFullName().getSimpleName(),
              domain,
              rawValue,
              unwrapOptional(rawValue));
      return converted.emitted()
          ? MemberConversion.emitted(converted.value())
          : MemberConversion.omitted();
    } catch (RuntimeException e) {
      throw new JsonApiMappingException(
          MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE,
          domain.getClass(),
          memberLocation(nativeProperty, PropertyRole.ATTRIBUTE),
          "Failed to serialize attribute '" + nativeProperty.logicalName() + "'",
          e);
    }
  }

  @Override
  public WriteResourceDefinition<MappingProperty> definition(JavaType declaredType) {
    // Identity extraction for linked targets revisits one declared type many times per write, so
    // the immutable neutral view is memoized alongside the adapter mapping cache that owns the
    // resolved mapping it is built from.
    return definitions.computeIfAbsent(declaredType, this::buildDefinition);
  }

  private WriteResourceDefinition<MappingProperty> buildDefinition(JavaType declaredType) {
    ResourceMapping mapping = mappingFor(declaredType);
    List<WriteProperty<MappingProperty>> attributes = new ArrayList<>(mapping.attributes().size());
    for (MappingProperty property : mapping.attributes()) {
      attributes.add(writeProperty(property));
    }
    List<WriteProperty<MappingProperty>> relationships =
        new ArrayList<>(mapping.relationships().size());
    for (MappingProperty property : mapping.relationships()) {
      relationships.add(writeProperty(property));
    }
    List<WriteProperty<MappingProperty>> relationshipMeta =
        new ArrayList<>(mapping.relationshipMetaProperties().size());
    for (MappingProperty property : mapping.relationshipMetaProperties()) {
      relationshipMeta.add(writeProperty(property));
    }
    return new WriteResourceDefinition<>(
        mapping.resourceType(),
        writeProperty(mapping.identifierProperty()),
        writeProperty(mapping.localIdProperty()),
        attributes,
        relationships,
        writeProperty(mapping.resourceMeta()),
        relationshipMeta);
  }

  @Override
  public @Nullable Object readValue(Object domain, WriteProperty<MappingProperty> property) {
    return readValue(domain, property.token(), property.role());
  }

  @Override
  public @Nullable String convertIdentifier(@Nullable Object value) {
    return identifierConverter.convert(value);
  }

  private static @Nullable WriteProperty<MappingProperty> writeProperty(
      @Nullable MappingProperty property) {
    return property == null ? null : new WriteProperty<>(property, property.metadata());
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
}
