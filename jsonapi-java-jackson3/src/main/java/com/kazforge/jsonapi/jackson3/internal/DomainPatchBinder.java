package com.kazforge.jsonapi.jackson3.internal;

import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import com.kazforge.jsonapi.jackson3.mapping.RelationshipLinkageMapper;
import com.kazforge.jsonapi.mapping.IdentifierConverter;
import com.kazforge.jsonapi.mapping.internal.PatchCommandBinder;
import com.kazforge.jsonapi.mapping.internal.PatchProperty;
import com.kazforge.jsonapi.mapping.internal.PatchResourceBackend;
import com.kazforge.jsonapi.mapping.internal.PatchResourceDefinition;
import com.kazforge.jsonapi.mapping.internal.ReadRelationshipShape;
import com.kazforge.jsonapi.mapping.internal.StructuredPatchBinder;
import com.kazforge.jsonapi.patch.PatchCommand;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 native backend for the shared low-level PATCH command binder.
 *
 * <p>The Core-to-application PATCH semantics are owned by the shared {@link PatchCommandBinder}:
 * resource-type matching, required {@code id} identity, supplied-member classification, lookup by
 * JSON:API name, change construction, {@code PatchCommand} assembly, and the complete phase order.
 * Whole linkage replacement, cardinality, direct identifier copies, wrapper occurrence
 * orchestration, and identifier-meta sequencing are shared with ordinary flat reads through {@code
 * RelationshipLinkageBinder}. This adapter supplies only the native edges through {@link
 * PatchResourceBackend}: declared whole-meta target validation, identity parsing/conversion, atomic
 * and recursively structured attribute and meta conversion, final relationship target/container
 * coercion, lazy relationship-shape resolution, configured linkage-mapper invocation, and
 * identifier-meta conversion.
 *
 * <p>The dedicated inbound PATCH definition is projected from the same deserialization-resolved
 * {@link ReadResourceMapping} authority as ordinary reads, so setter-only, creator-only, and
 * write-only/deserialization-only properties participate, while a supplied mapped property without
 * an effective deserialization target fails with {@code NON_DESERIALIZABLE_PROPERTY} at its wire
 * location instead of being converted from a serialization accessor. Document {@code included} is
 * never read. Identifier meta is not an independent {@link com.kazforge.jsonapi.patch.PatchChange};
 * it rides on {@code ResourceIdentifier} values inside {@code PatchChange.RelationshipChange} when
 * linkage is supplied.
 */
public final class DomainPatchBinder
    implements PatchResourceBackend<JavaType, ReadMappingProperty> {

  private final JsonMapper mapper;
  private final MappingDefinitionCache cache;
  private final Map<Class<?>, RelationshipLinkageMapper> linkageMappers;
  private final PatchMemberConverter converter;
  private final StructuredValueBinder structuredBinder;
  private final WholeMetaTarget wholeMetaTarget;
  private final PatchCommandBinder<JavaType, ReadMappingProperty> binder;

  public DomainPatchBinder(
      JsonMapper mapper,
      IdentifierConverter identifierConverter,
      MappingDefinitionCache cache,
      Map<Class<?>, RelationshipLinkageMapper> linkageMappers) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.cache = Objects.requireNonNull(cache, "cache");
    this.linkageMappers = Map.copyOf(Objects.requireNonNull(linkageMappers, "linkageMappers"));
    this.converter = new PatchMemberConverter(mapper, identifierConverter, linkageMappers);
    this.structuredBinder = new StructuredValueBinder(mapper);
    this.wholeMetaTarget = new WholeMetaTarget(mapper);
    this.binder = new PatchCommandBinder<>(this);
  }

  /** Binds one resource object into a presence-aware patch command for {@code targetType}. */
  @SuppressWarnings("java:S1452")
  public PatchCommand<?> fromResource(ResourceObject resource, JavaType targetType) {
    Objects.requireNonNull(resource, "resource");
    Objects.requireNonNull(targetType, "targetType");
    Class<?> rawType = targetType.getRawClass();
    PatchResourceDefinition<ReadMappingProperty> definition =
        cache.resolveRead(targetType).patchDefinition();
    return binder.bind(resource, definition, targetType, rawType);
  }

  @Override
  public Class<?> rawType(PatchProperty<ReadMappingProperty> property) {
    return property.token().type().getRawClass();
  }

  /**
   * Whole-meta declared-target validation for the low-level PATCH role, resolved from the dedicated
   * inbound PATCH projection: only effective/bindable properties contribute declared targets, so a
   * serialization-only declaration cannot block a PATCH that does not supply it.
   */
  @Override
  public void validateDeclaredMetaTargets(
      PatchResourceDefinition<ReadMappingProperty> definition,
      JavaType beanType,
      Class<?> rawType) {
    wholeMetaTarget.validateDeclaredPatchTargets(definition, rawType);
  }

  @Override
  public Object convertIdentity(
      String wireIdentifier,
      PatchProperty<ReadMappingProperty> identifier,
      JavaType beanType,
      Class<?> rawType) {
    return converter.convertIdentity(wireIdentifier, identifier.token(), beanType, rawType);
  }

  @Override
  public @Nullable Object convertAttribute(
      PatchProperty<ReadMappingProperty> property,
      @Nullable Object rawValue,
      JavaType beanType,
      MappingLocation location,
      Class<?> rawType) {
    return bindMember(property.token(), rawValue, beanType, location, rawType, false);
  }

  @Override
  public @Nullable Object convertWholeMeta(
      PatchProperty<ReadMappingProperty> property,
      @Nullable Object rawValue,
      JavaType beanType,
      MappingLocation location,
      Class<?> rawType) {
    return bindMember(property.token(), rawValue, beanType, location, rawType, true);
  }

  /**
   * Converts one supplied attribute or whole-meta value through the property-scoped Jackson
   * authority, delegating object wire values to the adapter-local {@link StructuredValueBinder} for
   * existing recursive structured behavior. A null attribute value stays a present null change.
   */
  private @Nullable Object bindMember(
      ReadMappingProperty property,
      @Nullable Object rawValue,
      JavaType beanType,
      MappingLocation location,
      Class<?> rawType,
      boolean wholeMeta) {
    JavaType declaredType = property.type();
    if (!wholeMeta && rawValue == null) {
      return converter.convertAttribute(property, null, declaredType, rawType, beanType, location);
    }
    StructuredPatchBinder.LowLevelKind kind =
        structuredBinder.lowLevelKind(
            declaredType,
            rawValue,
            property.serializationMember(),
            property.definition().getMutator(),
            location,
            rawType);
    if (kind == StructuredPatchBinder.LowLevelKind.RECURSE) {
      return structuredBinder.bindLowLevelStructured(rawValue, declaredType, location, rawType);
    }
    JavaType targetType = PatchMemberConverter.unwrapPatchPresence(declaredType);
    return wholeMeta
        ? converter.convertWholeMeta(property, rawValue, targetType, beanType, location, rawType)
        : converter.convertAttribute(property, rawValue, targetType, rawType, beanType, location);
  }

  @Override
  public ReadRelationshipShape<JavaType> readRelationshipShape(
      PatchProperty<ReadMappingProperty> property) {
    ReadMappingProperty nativeProperty = property.token();
    return RelationshipLinkageSupport.readRelationshipShape(
        nativeProperty.type(), nativeProperty, linkageMappers, mapper.getTypeFactory());
  }

  @Override
  public @Nullable Object mapLinkage(
      PatchProperty<ReadMappingProperty> property, RelationshipData data, JavaType target) {
    return RelationshipLinkageSupport.mapLinkage(data, target, property.token(), linkageMappers);
  }

  @Override
  public @Nullable Object convertIdentifierMeta(
      PatchProperty<ReadMappingProperty> property,
      Meta meta,
      JavaType metaToken,
      int occurrenceIndex) {
    return RelationshipLinkageSupport.convertIdentifierMeta(
        meta, metaToken, mapper, property.token(), occurrenceIndex);
  }

  @Override
  public @Nullable Object coerceRelationship(
      PatchProperty<ReadMappingProperty> property, @Nullable Object value) {
    return converter.coerceRelationship(property.token(), value);
  }
}
