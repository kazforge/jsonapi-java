package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.diagnostic.MappingLocation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Mapping-local test double for {@link WriteResourceBackend} over string type and property tokens.
 * Test code configures definitions, domain values, relationship shapes, and per-member conversion
 * outcomes directly; no write semantics beyond that configuration are simulated.
 *
 * <p>It also carries passive observations of the native callbacks the shared writer invokes: target
 * resolution, whole-meta conversion, and identifier-meta conversion. The observers record
 * invocations and return configured or deterministic results; they implement no Jackson behavior.
 */
@NullMarked
final class MappingFakeWriteResourceBackend implements WriteResourceBackend<String, String> {

  final Map<String, WriteResourceDefinition<String>> definitions = new LinkedHashMap<>();
  final Map<Object, Map<String, @Nullable Object>> values = new LinkedHashMap<>();
  final Map<Object, String> effectiveTypes = new LinkedHashMap<>();
  final Set<String> omittedAttributes = new LinkedHashSet<>();
  final Set<Object> unconvertibleValues = new LinkedHashSet<>();

  /** Declared relationship shapes by relationship property token. */
  final Map<String, RelationshipShape<String>> relationshipShapes = new LinkedHashMap<>();

  /**
   * Whole-meta conversion outcomes by meta property token; absent means emitted unwrapped value.
   */
  final Map<String, MemberConversion> wholeMetaConversions = new LinkedHashMap<>();

  /**
   * Identifier-meta conversion outcomes by declared meta token; absent means a deterministic map.
   */
  final Map<String, MemberConversion> identifierMetaConversions = new LinkedHashMap<>();

  /** Whole-meta property tokens whose conversion throws. */
  final Set<String> failingWholeMeta = new LinkedHashSet<>();

  /** Declared identifier-meta tokens whose conversion throws. */
  final Set<String> failingIdentifierMeta = new LinkedHashSet<>();

  /** Target-resolution observations of the shared writer, in call order. */
  final List<TargetResolution> targetResolutions = new ArrayList<>();

  /** Identifier-meta conversion observations of the shared writer, in call order. */
  final List<IdentifierMetaObservation> identifierMetaObservations = new ArrayList<>();

  /** One target-resolution invocation: target, declared token, cardinality, and location. */
  record TargetResolution(
      @Nullable Object target,
      @Nullable String declaredTargetToken,
      boolean relationshipToMany,
      MappingLocation location) {}

  /** One identifier-meta conversion invocation: declared meta token and occurrence value. */
  record IdentifierMetaObservation(
      @Nullable String declaredMetaToken, @Nullable Object metaValue) {}

  static WriteProperty<String> property(
      PropertyRole role, String logicalName, String externalName, String jsonapiName) {
    return new WriteProperty<>(
        logicalName, new SemanticProperty(role, logicalName, externalName, jsonapiName));
  }

  /** Registers one write definition, partitioning properties by their metadata role. */
  @SafeVarargs
  final void define(String resourceType, WriteProperty<String>... properties) {
    WriteProperty<String> identifier = null;
    WriteProperty<String> localId = null;
    WriteProperty<String> resourceMeta = null;
    List<WriteProperty<String>> attributes = new ArrayList<>();
    List<WriteProperty<String>> relationships = new ArrayList<>();
    List<WriteProperty<String>> relationshipMeta = new ArrayList<>();
    for (WriteProperty<String> property : properties) {
      switch (property.role()) {
        case ID -> identifier = property;
        case LOCAL_ID -> localId = property;
        case ATTRIBUTE -> attributes.add(property);
        case RELATIONSHIP -> relationships.add(property);
        case RESOURCE_META -> resourceMeta = property;
        case RELATIONSHIP_META -> relationshipMeta.add(property);
      }
    }
    definitions.put(
        resourceType,
        new WriteResourceDefinition<>(
            resourceType,
            identifier,
            localId,
            attributes,
            relationships,
            resourceMeta,
            relationshipMeta));
  }

  /** Sets one domain's value for a property token; presence is explicit. */
  void value(Object domain, String propertyToken, @Nullable Object value) {
    values.computeIfAbsent(domain, ignored -> new LinkedHashMap<>()).put(propertyToken, value);
  }

  void mapEffectiveType(Object domain, String type) {
    effectiveTypes.put(domain, type);
  }

  void omitAttribute(String propertyToken) {
    omittedAttributes.add(propertyToken);
  }

  /** Configures a raw value whose identifier conversion yields no wire string. */
  void unconvertibleIdentity(Object value) {
    unconvertibleValues.add(value);
  }

  /** Registers the declared shape of one relationship property. */
  void relationshipShape(String propertyToken, RelationshipShape<String> shape) {
    relationshipShapes.put(propertyToken, shape);
  }

  /** Configures one whole-meta property's conversion outcome. */
  void wholeMetaConversion(String propertyToken, MemberConversion conversion) {
    wholeMetaConversions.put(propertyToken, conversion);
  }

  /** Configures one declared identifier-meta token's conversion outcome. */
  void identifierMetaConversion(String declaredMetaToken, MemberConversion conversion) {
    identifierMetaConversions.put(declaredMetaToken, conversion);
  }

  /** Configures one whole-meta property whose conversion throws. */
  void failWholeMetaConversion(String propertyToken) {
    failingWholeMeta.add(propertyToken);
  }

  /** Configures one declared identifier-meta token whose conversion throws. */
  void failIdentifierMetaConversion(String declaredMetaToken) {
    failingIdentifierMeta.add(declaredMetaToken);
  }

  @Override
  public WriteResourceDefinition<String> definition(String declaredType) {
    WriteResourceDefinition<String> definition = definitions.get(declaredType);
    if (definition == null) {
      throw new IllegalArgumentException("no definition for " + declaredType);
    }
    return definition;
  }

  @Override
  public @Nullable Object readValue(Object domain, WriteProperty<String> property) {
    Map<String, @Nullable Object> domainValues = values.get(domain);
    return domainValues == null ? null : domainValues.get(property.token());
  }

  @Override
  public @Nullable String convertIdentifier(@Nullable Object value) {
    if (value == null || unconvertibleValues.contains(value)) {
      return null;
    }
    return String.valueOf(value);
  }

  @Override
  public MemberConversion convertAttribute(
      Object domain, String declaredType, WriteProperty<String> property) {
    Map<String, @Nullable Object> domainValues = values.get(domain);
    if (domainValues == null || !domainValues.containsKey(property.token())) {
      return MemberConversion.omitted();
    }
    if (omittedAttributes.contains(property.token())) {
      return MemberConversion.omitted();
    }
    Object raw = domainValues.get(property.token());
    return MemberConversion.emitted(raw == null ? null : "converted:" + raw);
  }

  @Override
  public MemberConversion convertWholeMeta(
      Object domain,
      String declaredType,
      WriteProperty<String> property,
      @Nullable Object rawValue,
      @Nullable Object unwrappedValue) {
    if (failingWholeMeta.contains(property.token())) {
      throw new IllegalStateException("whole-meta conversion failed for " + property.token());
    }
    MemberConversion configured = wholeMetaConversions.get(property.token());
    return configured != null ? configured : MemberConversion.emitted(unwrappedValue);
  }

  @Override
  public MemberConversion convertIdentifierMeta(
      String declaredMetaToken, @Nullable Object metaValue) {
    identifierMetaObservations.add(new IdentifierMetaObservation(declaredMetaToken, metaValue));
    if (failingIdentifierMeta.contains(declaredMetaToken)) {
      throw new IllegalStateException("identifier-meta conversion failed for " + declaredMetaToken);
    }
    MemberConversion configured = identifierMetaConversions.get(declaredMetaToken);
    return configured != null
        ? configured
        : MemberConversion.emitted(
            Map.of("observed:" + declaredMetaToken, String.valueOf(metaValue)));
  }

  @Override
  public RelationshipShape<String> relationshipShape(WriteProperty<String> property) {
    RelationshipShape<String> shape = relationshipShapes.get(property.token());
    if (shape == null) {
      throw new IllegalArgumentException("no relationship shape for " + property.token());
    }
    return shape;
  }

  @Override
  public String resolveRelationshipTarget(
      @Nullable Object target,
      @Nullable String declaredTarget,
      boolean relationshipToMany,
      MappingLocation relationshipLocation) {
    targetResolutions.add(
        new TargetResolution(target, declaredTarget, relationshipToMany, relationshipLocation));
    return effectiveType(Objects.requireNonNull(target), Objects.requireNonNull(declaredTarget));
  }

  @Override
  public String effectiveType(Object domain, String declaredType) {
    return effectiveTypes.getOrDefault(domain, declaredType);
  }
}
