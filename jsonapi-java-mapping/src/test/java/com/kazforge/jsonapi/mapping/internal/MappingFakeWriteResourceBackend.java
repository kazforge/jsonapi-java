package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.RelationshipData;
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
 * Test code configures definitions, domain values, and per-property behavior directly; no write
 * semantics beyond that configuration are simulated.
 */
@NullMarked
final class MappingFakeWriteResourceBackend implements WriteResourceBackend<String, String> {

  final Map<String, WriteResourceDefinition<String>> definitions = new LinkedHashMap<>();
  final Map<Object, Map<String, @Nullable Object>> values = new LinkedHashMap<>();
  final Map<Object, String> effectiveTypes = new LinkedHashMap<>();
  final Map<String, String> relationshipTargetTypes = new LinkedHashMap<>();
  final Set<String> toManyRelationships = new LinkedHashSet<>();
  final Set<String> omittedAttributes = new LinkedHashSet<>();
  final Set<String> unconvertibleIdentities = new LinkedHashSet<>();
  final Map<String, Meta> relationshipMeta = new LinkedHashMap<>();

  /** JSON:API names of relationships enriched, in call order. */
  final List<String> enrichmentOrder = new ArrayList<>();

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
    List<WriteProperty<String>> attributes = new ArrayList<>();
    List<WriteProperty<String>> relationships = new ArrayList<>();
    for (WriteProperty<String> property : properties) {
      switch (property.role()) {
        case ID -> identifier = property;
        case LOCAL_ID -> localId = property;
        case ATTRIBUTE -> attributes.add(property);
        case RELATIONSHIP -> relationships.add(property);
        case RESOURCE_META, RELATIONSHIP_META ->
            throw new IllegalArgumentException("basic write definitions carry no meta roles");
      }
    }
    definitions.put(
        resourceType,
        new WriteResourceDefinition<>(
            resourceType, identifier, localId, attributes, relationships));
  }

  /** Sets one domain's value for a property token; presence is explicit. */
  void value(Object domain, String propertyToken, @Nullable Object value) {
    values.computeIfAbsent(domain, ignored -> new LinkedHashMap<>()).put(propertyToken, value);
  }

  void mapEffectiveType(Object domain, String type) {
    effectiveTypes.put(domain, type);
  }

  void relationshipTarget(String propertyToken, String targetType) {
    relationshipTargetTypes.put(propertyToken, targetType);
  }

  void toMany(String propertyToken) {
    toManyRelationships.add(propertyToken);
  }

  void omitAttribute(String propertyToken) {
    omittedAttributes.add(propertyToken);
  }

  void unconvertibleIdentity(String propertyToken) {
    unconvertibleIdentities.add(propertyToken);
  }

  void relationshipMeta(String propertyToken, Meta meta) {
    relationshipMeta.put(propertyToken, meta);
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
  public IdentityRead identity(Object domain, WriteProperty<String> property) {
    Map<String, @Nullable Object> domainValues = values.get(domain);
    if (domainValues == null || !domainValues.containsKey(property.token())) {
      return IdentityRead.absent();
    }
    Object raw = domainValues.get(property.token());
    if (raw == null) {
      return IdentityRead.absent();
    }
    if (unconvertibleIdentities.contains(property.token())) {
      return IdentityRead.of(null);
    }
    return IdentityRead.of(String.valueOf(raw));
  }

  @Override
  public AttributeConversion attribute(
      Object domain, String declaredType, WriteProperty<String> property) {
    Map<String, @Nullable Object> domainValues = values.get(domain);
    if (domainValues == null || !domainValues.containsKey(property.token())) {
      return AttributeConversion.omitted();
    }
    if (omittedAttributes.contains(property.token())) {
      return AttributeConversion.omitted();
    }
    Object raw = domainValues.get(property.token());
    return raw == null
        ? AttributeConversion.emitted(null)
        : AttributeConversion.emitted("converted:" + raw);
  }

  @Override
  public RelationshipValue<String> normalizeRelationship(
      Object domain, String declaredType, WriteProperty<String> property) {
    Object raw = domainValue(domain, property.token());
    return switch (raw) {
      case null ->
          toManyRelationships.contains(property.token())
              ? new RelationshipValue.ToMany<>(List.of(), null)
              : new RelationshipValue.ToOne<>(null, null);
      case RelationshipData data -> new RelationshipValue.Linkage<>(data);
      case List<?> targets ->
          new RelationshipValue.ToMany<>(
              nonNullTargets(targets),
              Objects.requireNonNull(relationshipTargetTypes.get(property.token())));
      default ->
          new RelationshipValue.ToOne<>(
              raw, Objects.requireNonNull(relationshipTargetTypes.get(property.token())));
    };
  }

  private static List<Object> nonNullTargets(List<?> targets) {
    List<Object> domainTargets = new ArrayList<>();
    for (Object target : targets) {
      if (target != null) {
        domainTargets.add(target);
      }
    }
    return domainTargets;
  }

  @Override
  public String effectiveType(Object domain, String declaredType) {
    return effectiveTypes.getOrDefault(domain, declaredType);
  }

  @Override
  public Relationship enrichRelationship(
      Object domain,
      String declaredType,
      WriteProperty<String> property,
      RelationshipData linkage) {
    enrichmentOrder.add(property.jsonapiName());
    return new Relationship(linkage, null, relationshipMeta.get(property.token()), Map.of());
  }

  private @Nullable Object domainValue(Object domain, String propertyToken) {
    Map<String, @Nullable Object> domainValues = values.get(domain);
    if (domainValues == null || !domainValues.containsKey(propertyToken)) {
      return null;
    }
    return domainValues.get(propertyToken);
  }
}
