package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.internal.mapping.IdentifierMetaSupport;
import com.kazforge.jsonapi.mapping.IdentifierConverter;
import com.kazforge.jsonapi.mapping.RelationshipLinkage;
import com.kazforge.jsonapi.mapping.internal.AttributeConversion;
import com.kazforge.jsonapi.mapping.internal.BasicResourceWriter;
import com.kazforge.jsonapi.mapping.internal.IdentityRead;
import com.kazforge.jsonapi.mapping.internal.PropertyRole;
import com.kazforge.jsonapi.mapping.internal.RelationshipValue;
import com.kazforge.jsonapi.mapping.internal.WriteProperty;
import com.kazforge.jsonapi.mapping.internal.WriteResourceBackend;
import com.kazforge.jsonapi.mapping.internal.WriteResourceDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Jackson 2 bridge exposing only the native capabilities the shared basic resource writer cannot
 * own: mapping lookup, property reads, configured identifier and attribute conversion, declared
 * cardinality and target resolution, ordinary relationship normalization, and relationship
 * enrichment.
 *
 * <p>Direct identifiers and linkage data, {@link RelationshipLinkage} wrappers, and their
 * identifier meta stay adapter-owned and reach the shared writer as prebuilt linkage. Whole-meta
 * and decoration phases remain in {@link DomainResourceWriter}, which applies them around the
 * shared writer.
 */
final class Jackson2WriteResourceBackend
    implements WriteResourceBackend<JavaType, MappingProperty> {

  private final MappingDefinitionCache cache;
  private final IdentifierConverter identifierConverter;
  private final PropertyScopedValueConverter propertyScoped;
  private final WholeMetaValueBuilder metaValues;
  private final BasicResourceWriter<JavaType, MappingProperty> writer;
  private final Map<JavaType, WriteResourceDefinition<MappingProperty>> definitions =
      new ConcurrentHashMap<>();

  Jackson2WriteResourceBackend(
      MappingDefinitionCache cache,
      IdentifierConverter identifierConverter,
      PropertyScopedValueConverter propertyScoped) {
    this.cache = Objects.requireNonNull(cache, "cache");
    this.identifierConverter = Objects.requireNonNull(identifierConverter, "identifierConverter");
    this.propertyScoped = Objects.requireNonNull(propertyScoped, "propertyScoped");
    this.metaValues = new WholeMetaValueBuilder(propertyScoped);
    this.writer = new BasicResourceWriter<>(this);
  }

  /** The shared basic writer this backend is composed with. */
  BasicResourceWriter<JavaType, MappingProperty> writer() {
    return writer;
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
    return new WriteResourceDefinition<>(
        mapping.resourceType(),
        writeProperty(mapping.identifierProperty()),
        writeProperty(mapping.localIdProperty()),
        attributes,
        relationships);
  }

  @Override
  public IdentityRead identity(Object domain, WriteProperty<MappingProperty> property) {
    Object raw = MappingPropertyAccess.readValue(domain, property.token(), property.role());
    Object value = MappingPropertyAccess.unwrapOptional(raw);
    if (value == null) {
      return IdentityRead.absent();
    }
    return IdentityRead.of(identifierConverter.convert(value));
  }

  @Override
  public AttributeConversion attribute(
      Object domain, JavaType declaredType, WriteProperty<MappingProperty> property) {
    MappingProperty nativeProperty = property.token();
    Object rawValue =
        MappingPropertyAccess.readValue(domain, nativeProperty, PropertyRole.ATTRIBUTE);
    if (rawValue instanceof Optional<?> optional && optional.isEmpty()) {
      return AttributeConversion.omitted();
    }
    try {
      PropertyScopedValueConverter.SerializationResult converted =
          propertyScoped.serialize(
              mappingFor(declaredType).domainType(),
              nativeProperty.definition().getFullName().getSimpleName(),
              domain,
              rawValue,
              MappingPropertyAccess.unwrapOptional(rawValue));
      return converted.emitted()
          ? AttributeConversion.emitted(converted.value())
          : AttributeConversion.omitted();
    } catch (RuntimeException e) {
      throw new JsonApiMappingException(
          MappingDiagnostic.UNSUPPORTED_ATTRIBUTE_VALUE,
          domain.getClass(),
          MappingPropertyAccess.memberLocation(nativeProperty, PropertyRole.ATTRIBUTE),
          "Failed to serialize attribute '" + nativeProperty.logicalName() + "'",
          e);
    }
  }

  @Override
  public RelationshipValue<JavaType> normalizeRelationship(
      Object domain, JavaType declaredType, WriteProperty<MappingProperty> property) {
    MappingProperty nativeProperty = property.token();
    Object value =
        MappingPropertyAccess.readValue(domain, nativeProperty, PropertyRole.RELATIONSHIP);
    JavaType propertyType = nativeProperty.accessor().getType();
    MappingLocation relationshipLocation =
        RelationshipMetaSupport.relationshipLocation(nativeProperty);
    return MappingTypeSupport.isToManyType(propertyType)
        ? normalizeToMany(domain, nativeProperty, value, propertyType, relationshipLocation)
        : normalizeToOne(domain, nativeProperty, value, propertyType);
  }

  @Override
  public JavaType effectiveType(Object domain, JavaType declaredType) {
    if (declaredType.getRawClass() == domain.getClass()) {
      return declaredType;
    }
    return cache.specializeType(declaredType, domain.getClass());
  }

  @Override
  public Relationship enrichRelationship(
      Object domain,
      JavaType declaredType,
      WriteProperty<MappingProperty> property,
      RelationshipData linkage) {
    ResourceMapping mapping = mappingFor(declaredType);
    MappingProperty metaProperty =
        RelationshipMetaSupport.byTarget(mapping.relationshipMetaProperties())
            .get(property.jsonapiName());
    Meta meta = null;
    if (metaProperty != null) {
      meta =
          metaValues.build(
              domain,
              mapping.domainType(),
              metaProperty,
              RelationshipMetaSupport.relationshipMetaLocation(property.jsonapiName()));
    }
    return new Relationship(linkage, null, meta, Map.of());
  }

  private RelationshipValue<JavaType> normalizeToOne(
      Object resource, MappingProperty property, @Nullable Object value, JavaType propertyType) {
    Object unwrapped = MappingPropertyAccess.unwrapOptional(value);
    JavaType linkageType = MappingTypeSupport.linkageJavaType(propertyType);
    if (linkageType != null
        || unwrapped instanceof ResourceIdentifier
        || unwrapped instanceof RelationshipData) {
      return new RelationshipValue.Linkage<>(
          advancedToOneLinkage(resource, property, value, propertyType));
    }
    if (unwrapped == null) {
      return new RelationshipValue.ToOne<>(null, null);
    }
    return new RelationshipValue.ToOne<>(
        unwrapped, relationshipTargetType(unwrapped, propertyType));
  }

  private RelationshipValue<JavaType> normalizeToMany(
      Object resource,
      MappingProperty property,
      @Nullable Object value,
      JavaType propType,
      MappingLocation relationshipLocation) {
    if (value == null) {
      return new RelationshipValue.ToMany<>(List.of(), null);
    }
    JavaType linkageType = MappingTypeSupport.linkageJavaType(propType);
    if (linkageType != null) {
      List<Object> items = MappingPropertyAccess.convertToCollection(value, relationshipLocation);
      if (items.isEmpty()) {
        return new RelationshipValue.ToMany<>(List.of(), null);
      }
      return new RelationshipValue.Linkage<>(
          toManyWrappedLinkage(resource, property, items, linkageType, relationshipLocation));
    }
    List<Object> items = MappingPropertyAccess.convertToCollection(value, relationshipLocation);
    if (items.isEmpty()) {
      return new RelationshipValue.ToMany<>(List.of(), null);
    }
    return switch (classifyToManyItems(items)) {
      case ResourceIdentifiers(List<ResourceIdentifier> identifiers) ->
          new RelationshipValue.Linkage<>(
              new RelationshipData.IdentifierCollectionLinkage(identifiers));
      case DomainObjects(List<?> domainItems) ->
          ordinaryToMany(domainItems, propType, relationshipLocation);
      case Mixed(Object firstNonResourceIdentifier) ->
          throw mixedToManyElements(firstNonResourceIdentifier, relationshipLocation);
    };
  }

  /**
   * Ordinary domain targets stay domain values; the shared writer extracts effective-type identity
   * for each. Declared target validation through the configured-Jackson metadata authority stays
   * here so the stable diagnostic and class-based check cannot drift.
   */
  private RelationshipValue<JavaType> ordinaryToMany(
      List<?> items, JavaType propType, MappingLocation relationshipLocation) {
    JavaType contentType = MappingTypeSupport.resolveContentType(propType);
    if (contentType == null) {
      throw new JsonApiMappingException(
          MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_COLLECTION_TYPE,
          null,
          relationshipLocation,
          "Cannot resolve collection content type");
    }
    checkDeclaredTargetHasResourceMetadata(contentType, relationshipLocation);
    return new RelationshipValue.ToMany<>(List.copyOf(items), contentType);
  }

  /**
   * Builds linkage for direct or wrapper forms the shared writer must not reinterpret. Wrapper
   * identifier meta stays in this prebuilt result.
   */
  private RelationshipData advancedToOneLinkage(
      Object resource, MappingProperty property, @Nullable Object value, JavaType propertyType) {
    Object unwrapped = MappingPropertyAccess.unwrapOptional(value);
    JavaType linkageType = MappingTypeSupport.linkageJavaType(propertyType);
    if (linkageType != null) {
      if (unwrapped == null) {
        return RelationshipData.NullLinkage.INSTANCE;
      }
      if (!(unwrapped instanceof RelationshipLinkage<?, ?>(Object target, Object meta))) {
        throw new JsonApiMappingException(
            MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_VALUE,
            unwrapped.getClass(),
            RelationshipMetaSupport.relationshipLocation(property),
            "Relationship '"
                + property.logicalName()
                + "' requires RelationshipLinkage values, got "
                + unwrapped.getClass().getName());
      }
      RelationshipData data =
          advancedToOneLinkage(
              resource, property, target, MappingTypeSupport.linkageTargetType(linkageType));
      return applyWrapperMeta(
          resource,
          MappingTypeSupport.linkageMetaType(linkageType),
          meta,
          data,
          property.jsonapiName(),
          -1);
    }
    return switch (unwrapped) {
      case null -> RelationshipData.NullLinkage.INSTANCE;
      case ResourceIdentifier resourceIdentifier ->
          new RelationshipData.SingleLinkage(resourceIdentifier);
      case RelationshipData relationshipData -> relationshipData;
      default ->
          new RelationshipData.SingleLinkage(
              writer.identifier(
                  Objects.requireNonNull(unwrapped),
                  relationshipTargetType(unwrapped, propertyType)));
    };
  }

  private RelationshipData toManyWrappedLinkage(
      Object resource,
      MappingProperty property,
      List<Object> items,
      JavaType linkageType,
      MappingLocation relationshipLocation) {
    JavaType targetType = MappingTypeSupport.linkageTargetType(linkageType);
    JavaType metaType = MappingTypeSupport.linkageMetaType(linkageType);
    List<ResourceIdentifier> identifiers = new ArrayList<>();
    int index = 0;
    for (Object item : items) {
      Object unwrappedItem = MappingPropertyAccess.unwrapOptional(item);
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
      RelationshipData data = advancedToOneLinkage(resource, property, target, targetType);
      RelationshipData overlaid =
          applyWrapperMeta(resource, metaType, meta, data, property.jsonapiName(), index);
      if (overlaid instanceof RelationshipData.SingleLinkage(ResourceIdentifier identifier)) {
        identifiers.add(identifier);
        index++;
      }
    }
    return new RelationshipData.IdentifierCollectionLinkage(identifiers);
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
    Meta meta = metaValues.fromConvertedIdentifierMeta(converted, resource, metaLocation);
    if (linkage instanceof RelationshipData.SingleLinkage(ResourceIdentifier identifier)) {
      return new RelationshipData.SingleLinkage(IdentifierMetaSupport.withMeta(identifier, meta));
    }
    throw new JsonApiMappingException(
        MappingDiagnostic.INVALID_IDENTIFIER_META_TARGET,
        resource.getClass(),
        metaLocation,
        "Identifier meta requires to-one linkage for relationship '" + relationshipName + "'");
  }

  private JavaType relationshipTargetType(Object value, JavaType propertyType) {
    JavaType unwrapped = MappingTypeSupport.unwrapOptionalType(propertyType);
    if (unwrapped.getRawClass() == Object.class
        || (unwrapped.getRawClass() == Optional.class && unwrapped.containedTypeCount() == 0)) {
      return cache.constructType(value.getClass());
    }
    return effectiveType(value, unwrapped);
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

  private static JsonApiMappingException mixedToManyElements(
      Object firstNonResourceIdentifier, MappingLocation relationshipLocation) {
    return new JsonApiMappingException(
        MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_VALUE,
        firstNonResourceIdentifier.getClass(),
        relationshipLocation,
        "Mixed element types in to-many relationship collection: expected ResourceIdentifier, got "
            + firstNonResourceIdentifier.getClass().getName());
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

  private ResourceMapping mappingFor(JavaType declaredType) {
    return ResolvedTypeSupport.requireMapping(cache, declaredType);
  }

  private static @Nullable WriteProperty<MappingProperty> writeProperty(
      @Nullable MappingProperty property) {
    return property == null ? null : new WriteProperty<>(property, property.metadata());
  }

  private sealed interface ToManyClassification permits ResourceIdentifiers, DomainObjects, Mixed {}

  private record ResourceIdentifiers(List<ResourceIdentifier> identifiers)
      implements ToManyClassification {}

  private record DomainObjects(List<?> items) implements ToManyClassification {}

  private record Mixed(Object firstNonResourceIdentifier) implements ToManyClassification {}
}
