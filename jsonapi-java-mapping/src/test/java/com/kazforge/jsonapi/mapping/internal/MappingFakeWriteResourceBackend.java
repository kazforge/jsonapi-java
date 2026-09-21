package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.Relationships;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Mapping-local test double for {@link WriteResourceBackend} and the relationship phase over string
 * type and property tokens. Test code configures definitions, domain values, and per-property
 * behavior directly; no write semantics beyond that configuration are simulated.
 */
@NullMarked
final class MappingFakeWriteResourceBackend implements WriteResourceBackend<String, String> {

  final Map<String, WriteResourceDefinition<String>> definitions = new LinkedHashMap<>();
  final Map<Object, Map<String, @Nullable Object>> values = new LinkedHashMap<>();
  final Map<Object, String> effectiveTypes = new LinkedHashMap<>();
  final Set<String> omittedAttributes = new LinkedHashSet<>();
  final Set<Object> unconvertibleValues = new LinkedHashSet<>();

  /** Relationship members returned by the phase, by relationship property token. */
  final Map<String, Relationship> relationshipMembers = new LinkedHashMap<>();

  /** JSON:API names passed to the phase, in call order. */
  final List<String> relationshipPhaseOrder = new ArrayList<>();

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

  void omitAttribute(String propertyToken) {
    omittedAttributes.add(propertyToken);
  }

  /** Configures a raw value whose identifier conversion yields no wire string. */
  void unconvertibleIdentity(Object value) {
    unconvertibleValues.add(value);
  }

  void relationshipMember(String propertyToken, Relationship relationship) {
    relationshipMembers.put(propertyToken, relationship);
  }

  /** Relationship phase double: returns the configured members for the selected properties. */
  @SuppressWarnings("unused")
  Relationships writeRelationships(
      Object resource, String declaredType, List<WriteProperty<String>> selectedRelationships) {
    Map<String, @Nullable Relationship> members = new LinkedHashMap<>();
    for (WriteProperty<String> property : selectedRelationships) {
      relationshipPhaseOrder.add(property.jsonapiName());
      members.put(property.jsonapiName(), relationshipMembers.get(property.token()));
    }
    return Relationships.ofRelationships(members);
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
  public AttributeConversion convertAttribute(
      Object domain, String declaredType, WriteProperty<String> property) {
    Map<String, @Nullable Object> domainValues = values.get(domain);
    if (domainValues == null || !domainValues.containsKey(property.token())) {
      return AttributeConversion.omitted();
    }
    if (omittedAttributes.contains(property.token())) {
      return AttributeConversion.omitted();
    }
    Object raw = domainValues.get(property.token());
    return AttributeConversion.emitted(raw == null ? null : "converted:" + raw);
  }

  @Override
  public String effectiveType(Object domain, String declaredType) {
    return effectiveTypes.getOrDefault(domain, declaredType);
  }
}
