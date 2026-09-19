package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.model.ResourceIdentity;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.jackson.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.jackson.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.jackson.representation.RepresentationPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Backend-neutral state owned by the shared compound-inclusion mapping engine. */
final class MappingCompoundInclusionState {

  private final RepresentationPolicy policy;
  private final Set<ResourceIdentity> primaryIdentities = new HashSet<>();
  private final Map<ResourceIdentity, ResourceObject> includedByIdentity = new LinkedHashMap<>();
  private final List<ResourceObject> includedInOrder = new ArrayList<>();
  private final Set<ResourceIdentity> linkageExemptions = new LinkedHashSet<>();

  MappingCompoundInclusionState(RepresentationPolicy policy) {
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  void registerPrimary(ResourceObject primary) {
    primaryIdentities.addAll(identityKeysOf(primary.type(), primary.id(), primary.lid()));
  }

  boolean matchesPrimary(ResourceIdentifier identifier) {
    for (ResourceIdentity key : identityKeysOf(identifier)) {
      if (primaryIdentities.contains(key)) {
        return true;
      }
    }
    return false;
  }

  @Nullable ResourceIdentity preferredIdentity(ResourceIdentifier identifier) {
    if (identifier.hasId()) {
      return ResourceIdentity.ofId(identifier.type(), Objects.requireNonNull(identifier.id()));
    }
    if (identifier.hasLid()) {
      return ResourceIdentity.ofLid(identifier.type(), Objects.requireNonNull(identifier.lid()));
    }
    return null;
  }

  void addLinkageExemption(ResourceIdentifier identifier) {
    ResourceIdentity identity = preferredIdentity(identifier);
    if (identity != null) {
      linkageExemptions.add(identity);
    }
  }

  void offerIncluded(ResourceObject candidate, String propertyPath) {
    List<ResourceIdentity> keys = identityKeysOf(candidate.type(), candidate.id(), candidate.lid());
    if (keys.isEmpty()) {
      return;
    }
    for (ResourceIdentity key : keys) {
      ResourceObject existing = includedByIdentity.get(key);
      if (existing != null) {
        if (!existing.equals(candidate)) {
          throw JsonApiMappingException.withoutLocation(
              MappingDiagnostic.CONFLICTING_INCLUDED_REPRESENTATION,
              null,
              "Conflicting included representation for "
                  + key
                  + " reached via include path '"
                  + propertyPath
                  + "'");
        }
        return;
      }
    }
    if (includedInOrder.size() >= policy.maxIncludedResources()) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.INCLUDE_COUNT_EXCEEDED,
          null,
          "Included resource count exceeds maxIncludedResources "
              + policy.maxIncludedResources()
              + " via include path '"
              + propertyPath
              + "'");
    }
    for (ResourceIdentity key : keys) {
      includedByIdentity.put(key, candidate);
    }
    includedInOrder.add(candidate);
  }

  MappingIncludedResult result() {
    return new MappingIncludedResult(List.copyOf(includedInOrder), linkageExemptions);
  }

  private static List<ResourceIdentity> identityKeysOf(ResourceIdentifier identifier) {
    return identityKeysOf(identifier.type(), identifier.id(), identifier.lid());
  }

  private static List<ResourceIdentity> identityKeysOf(
      String type, @Nullable String id, @Nullable String lid) {
    List<ResourceIdentity> keys = new ArrayList<>(2);
    if (id != null) {
      keys.add(ResourceIdentity.ofId(type, id));
    }
    if (lid != null) {
      keys.add(ResourceIdentity.ofLid(type, lid));
    }
    return keys;
  }
}
