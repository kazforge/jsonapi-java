package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.Relationships;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.mapping.IdentifierConverter;
import com.kazforge.jsonapi.mapping.RelationshipDecoration;
import com.kazforge.jsonapi.mapping.ResourceDecoration;
import com.kazforge.jsonapi.mapping.ResourceDecorator;
import com.kazforge.jsonapi.mapping.ResourceDecoratorRegistry;
import com.kazforge.jsonapi.mapping.internal.BasicResourceWriter;
import com.kazforge.jsonapi.mapping.internal.EffectiveRepresentation;
import com.kazforge.jsonapi.mapping.internal.PropertyRole;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Jackson 2 configured write orchestration.
 *
 * <p>Basic JSON:API type, identity, attribute, ordinary relationship-linkage, fieldset, and
 * base-resource assembly semantics are delegated to the shared {@link BasicResourceWriter} through
 * {@link Jackson2WriteResourceBackend}. This class retains native mapping resolution, whole-meta
 * target validation and resource meta, decoration, and the adapter-local failure ordering around
 * those phases: meta targets are validated before the shared writer reads basic values, each
 * relationship is enriched immediately after its linkage is built, resource meta follows
 * relationships, and decoration is applied last.
 */
public final class DomainResourceWriter {

  private static final String RESOURCE = "resource";
  private static final String DECLARED_TYPE = "declaredType";
  private static final String REPRESENTATION = "representation";

  private final MappingDefinitionCache cache;
  private final WholeMetaTarget wholeMetaTarget;
  private final WholeMetaValueBuilder metaValues;
  private final ResourceDecoratorRegistry decoratorRegistry;
  private final Jackson2WriteResourceBackend writeBackend;
  private final BasicResourceWriter<JavaType, MappingProperty> basicWriter;

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
    this.cache = Objects.requireNonNull(cache, "cache");
    this.decoratorRegistry = Objects.requireNonNull(decoratorRegistry, "decoratorRegistry");
    this.wholeMetaTarget = new WholeMetaTarget(mapper);
    PropertyScopedValueConverter propertyScoped = new PropertyScopedValueConverter(mapper);
    this.metaValues = new WholeMetaValueBuilder(propertyScoped);
    this.writeBackend =
        new Jackson2WriteResourceBackend(cache, identifierConverter, propertyScoped);
    this.basicWriter = writeBackend.writer();
  }

  public JavaType inferredType(Object resource) {
    Objects.requireNonNull(resource, RESOURCE);
    return cache.constructType(resource.getClass());
  }

  public JavaType effectiveType(Object resource, JavaType declaredType) {
    Objects.requireNonNull(resource, RESOURCE);
    Objects.requireNonNull(declaredType, DECLARED_TYPE);
    return writeBackend.effectiveType(resource, declaredType);
  }

  public ResourceObject toResource(Object resource, JavaType declaredType) {
    Objects.requireNonNull(resource, RESOURCE);
    Objects.requireNonNull(declaredType, DECLARED_TYPE);
    requireAssignable(resource, declaredType);
    ResourceMapping mapping = mappingFor(declaredType);
    validateMetaTargets(mapping, resource.getClass());
    ResourceObject base = basicWriter.writeBasic(resource, declaredType, null, false);
    return decorateResource(
        resource, declaredType, mapping, withResourceMeta(resource, mapping, base), null);
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
    return toResourceSelective(resource, declaredType, mapping, fields, false);
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
      @Nullable List<String> fields,
      boolean allowAbsentIdentity) {
    validateMetaTargets(mapping, resource.getClass());
    Set<String> allowedFields = fields == null ? null : Set.copyOf(fields);
    ResourceObject base =
        basicWriter.writeBasic(resource, declaredType, allowedFields, allowAbsentIdentity);
    ResourceObject withMeta = withResourceMeta(resource, mapping, base);
    return decorateResource(resource, declaredType, mapping, withMeta, allowedFields);
  }

  /** Applies the single mapped resource-meta property after all basic members are built. */
  @SuppressWarnings("NullAway")
  private ResourceObject withResourceMeta(
      Object resource, ResourceMapping mapping, ResourceObject base) {
    Meta meta = buildResourceMeta(resource, mapping);
    if (meta == null) {
      return base;
    }
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

  /** Builds the resource-side {@code meta} from the single mapped resource-meta property. */
  private @Nullable Meta buildResourceMeta(Object resource, ResourceMapping mapping) {
    MappingProperty resourceMetaProperty = mapping.resourceMeta();
    if (resourceMetaProperty == null) {
      return null;
    }
    return metaValues.build(
        resource,
        mapping.domainType(),
        resourceMetaProperty,
        RelationshipMetaSupport.resourceMetaLocation());
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
    return ResolvedTypeSupport.requireMapping(cache, declaredType);
  }

  /** Reads a relationship property for inclusion traversal (not linkage construction). */
  @Nullable Object readRelationshipValue(Object resource, MappingProperty property) {
    return MappingPropertyAccess.readValue(resource, property, PropertyRole.RELATIONSHIP);
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
    return MappingPropertyAccess.unwrapOptional(value);
  }

  /**
   * Locationless variant for callers without a JSON:API member coordinate (inclusion traversal).
   */
  static List<Object> convertToCollection(Object value) {
    return MappingPropertyAccess.convertToCollection(value, null);
  }
}
