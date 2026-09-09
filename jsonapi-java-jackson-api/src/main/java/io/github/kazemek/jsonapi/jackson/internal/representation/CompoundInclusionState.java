package io.github.kazemek.jsonapi.jackson.internal.representation;

import io.github.kazemek.jsonapi.core.model.ResourceIdentifier;
import io.github.kazemek.jsonapi.core.model.ResourceIdentity;
import io.github.kazemek.jsonapi.core.model.ResourceObject;
import io.github.kazemek.jsonapi.jackson.diagnostic.JsonApiMappingException;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingDiagnostic;
import io.github.kazemek.jsonapi.jackson.representation.RepresentationPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Jackson-free state for one compound-inclusion collection operation.
 *
 * <p>The adapters retain include-path validation and traversal. This state owns only identity alias
 * recognition, included-resource ordering and deduplication, sparse-fieldset exemptions, and the
 * count/conflict diagnostics needed while traversal offers already-rendered resources.
 */
public final class CompoundInclusionState {

  private final RepresentationPolicy policy;
  private final Set<ResourceIdentity> primaryIdentities = new HashSet<>();
  private final Map<ResourceIdentity, ResourceObject> includedByIdentity = new LinkedHashMap<>();
  private final List<ResourceObject> includedInOrder = new ArrayList<>();
  private final Set<ResourceIdentity> linkageExemptions = new LinkedHashSet<>();

  public CompoundInclusionState(RepresentationPolicy policy) {
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  /** Registers both id/lid identity aliases present on one primary resource. */
  public void registerPrimary(ResourceObject primary) {
    Objects.requireNonNull(primary, "primary");
    primaryIdentities.addAll(identityKeysOf(primary.type(), primary.id(), primary.lid()));
  }

  /** Returns whether an occurrence is one of the primary resources under either identity alias. */
  public boolean matchesPrimary(ResourceIdentifier identifier) {
    Objects.requireNonNull(identifier, "identifier");
    for (ResourceIdentity key : identityKeysOf(identifier)) {
      if (primaryIdentities.contains(key)) {
        return true;
      }
    }
    return false;
  }

  /** Returns the preferred id identity, falling back to the local-id identity when needed. */
  public @Nullable ResourceIdentity preferredIdentity(ResourceIdentifier identifier) {
    Objects.requireNonNull(identifier, "identifier");
    return preferredIdentityValue(identifier);
  }

  /** Records the preferred id-or-lid identity for a fieldset-omitted inbound linkage. */
  @SuppressWarnings("NullableProblems")
  public void addLinkageExemption(ResourceIdentifier identifier) {
    Objects.requireNonNull(identifier, "identifier");
    @Nullable ResourceIdentity preferred = preferredIdentityValue(identifier);
    if (preferred != null) {
      linkageExemptions.add(preferred);
    }
  }

  /**
   * Offers one already-rendered included resource, retaining first-seen order and rejecting a
   * conflicting representation for an identity alias.
   */
  public void offerIncluded(ResourceObject candidate, String propertyPath) {
    Objects.requireNonNull(candidate, "candidate");
    Objects.requireNonNull(propertyPath, "propertyPath");
    List<ResourceIdentity> keys = identityKeysOf(candidate.type(), candidate.id(), candidate.lid());
    if (keys.isEmpty()) {
      return;
    }
    ResourceIdentity matchedKey = null;
    for (ResourceIdentity key : keys) {
      if (includedByIdentity.containsKey(key)) {
        matchedKey = key;
        break;
      }
    }
    if (matchedKey != null) {
      ResourceObject existing = Objects.requireNonNull(includedByIdentity.get(matchedKey));
      if (!existing.equals(candidate)) {
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.CONFLICTING_INCLUDED_REPRESENTATION,
            null,
            "Conflicting included representation for "
                + matchedKey
                + " reached via include path '"
                + propertyPath
                + "'");
      }
      return;
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

  /** Returns a defensive result for this invocation. */
  public IncludedResourcesResult result() {
    return new IncludedResourcesResult(List.copyOf(includedInOrder), linkageExemptions);
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

  private static List<ResourceIdentity> identityKeysOf(ResourceIdentifier identifier) {
    return identityKeysOf(identifier.type(), identifier.id(), identifier.lid());
  }

  private static @Nullable ResourceIdentity preferredIdentityValue(ResourceIdentifier identifier) {
    if (identifier.hasId()) {
      return ResourceIdentity.ofId(identifier.type(), Objects.requireNonNull(identifier.id()));
    }
    if (identifier.hasLid()) {
      return ResourceIdentity.ofLid(identifier.type(), Objects.requireNonNull(identifier.lid()));
    }
    return null;
  }
}
