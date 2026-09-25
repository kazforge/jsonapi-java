package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
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
 * Mapping-local test double for {@link InclusionBackend} over string type tokens. Test code
 * configures relationship topology, values, identities, and rendered resources directly; no
 * behavior beyond that configuration is simulated.
 */
@NullMarked
final class MappingFakeInclusionBackend implements InclusionBackend<String> {

  final Map<String, Class<?>> rawClasses = new LinkedHashMap<>();
  final Map<String, Map<String, String>> relationships = new LinkedHashMap<>();
  final Map<Object, String> effectiveTypes = new LinkedHashMap<>();
  final Map<Object, ResourceIdentifier> identifiers = new LinkedHashMap<>();
  final Map<Object, ResourceObject> rendered = new LinkedHashMap<>();
  final Set<Object> identityLess = new LinkedHashSet<>();
  final Map<Object, Integer> renderCounts = new LinkedHashMap<>();

  /** Dotted include paths whose native related-type resolution fails. */
  final Set<String> unresolvedRelatedTypes = new LinkedHashSet<>();

  /** Dotted include paths for which related-type resolution was requested, in call order. */
  final List<String> relatedTypePaths = new ArrayList<>();

  private final Map<String, Map<String, @Nullable Object>> values = new LinkedHashMap<>();
  private final Map<Object, Map<String, @Nullable Object>> domainValues = new LinkedHashMap<>();
  private final Set<String> toOneRelationships = new LinkedHashSet<>();

  /** Configures related domain objects reached by one relationship of one owner type. */
  void relationshipValues(String ownerType, String relationshipName, Object... related) {
    values
        .computeIfAbsent(ownerType, ignored -> new LinkedHashMap<>())
        .put(relationshipName, List.of(related));
  }

  /** Configures related domain objects reached by one relationship of one specific domain. */
  void domainRelationshipValues(Object domain, String relationshipName, Object... related) {
    domainValues
        .computeIfAbsent(domain, ignored -> new LinkedHashMap<>())
        .put(relationshipName, List.of(related));
  }

  /** Configures the raw relationship property value for one specific domain. */
  void relationshipRawValue(Object domain, String relationshipName, @Nullable Object raw) {
    domainValues
        .computeIfAbsent(domain, ignored -> new LinkedHashMap<>())
        .put(relationshipName, raw);
  }

  /** Marks one relationship as declared to-one; unspecified relationships are to-many. */
  void toOneRelationship(String ownerType, String relationshipName) {
    toOneRelationships.add(ownerType + "." + relationshipName);
  }

  @Override
  public Class<?> rawClass(String type) {
    return rawClasses.getOrDefault(type, Object.class);
  }

  @Override
  public String resourceType(String type) {
    return type;
  }

  @Override
  public boolean hasRelationship(String ownerType, String relationshipName) {
    Map<String, String> ownerRelationships = relationships.get(ownerType);
    return ownerRelationships != null && ownerRelationships.containsKey(relationshipName);
  }

  @Override
  public String relatedType(String ownerType, String relationshipName, String dottedPath) {
    relatedTypePaths.add(dottedPath);
    if (unresolvedRelatedTypes.contains(dottedPath)) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.UNSUPPORTED_RELATIONSHIP_COLLECTION_TYPE,
          String.class,
          "Cannot resolve collection content type for include path '" + dottedPath + "'");
    }
    Map<String, String> ownerRelationships = relationships.get(ownerType);
    String resolved = ownerRelationships == null ? null : ownerRelationships.get(relationshipName);
    if (resolved == null) {
      throw new IllegalArgumentException(
          "Unknown relationship '" + relationshipName + "' on " + ownerType);
    }
    return resolved;
  }

  @Override
  public @Nullable Object relationshipValue(
      Object domain, String ownerType, String relationshipName) {
    Map<String, @Nullable Object> byDomain = domainValues.get(domain);
    if (byDomain != null && byDomain.containsKey(relationshipName)) {
      return byDomain.get(relationshipName);
    }
    Map<String, @Nullable Object> ownerValues = values.get(ownerType);
    if (ownerValues == null || !ownerValues.containsKey(relationshipName)) {
      return null;
    }
    return ownerValues.get(relationshipName);
  }

  @Override
  public boolean relationshipToMany(String ownerType, String relationshipName) {
    return !toOneRelationships.contains(ownerType + "." + relationshipName);
  }

  @Override
  public String effectiveType(Object domain, String declaredType) {
    return effectiveTypes.getOrDefault(domain, declaredType);
  }

  @Override
  public ResourceIdentifier identifier(Object domain, String type) {
    ResourceIdentifier registered = identifiers.get(domain);
    if (registered != null) {
      return registered;
    }
    // Unregistered domains get a synthetic identity derived from their test name so primary
    // roots and intermediate resources carry a visit key like mapped application objects would.
    return ResourceIdentifier.of(type, domain.toString());
  }

  @Override
  public ResourceObject render(Object domain, String type, EffectiveRepresentation representation) {
    renderCounts.merge(domain, 1, Integer::sum);
    return Objects.requireNonNull(rendered.get(domain), "no rendered resource for " + domain);
  }

  @Override
  public boolean hasIdentity(Object domain, String type) {
    return !identityLess.contains(domain);
  }
}
