package com.kazforge.jsonapi.core.validation;

import com.kazforge.jsonapi.core.internal.JsonPointers;
import com.kazforge.jsonapi.core.internal.SyntaxValidators;
import com.kazforge.jsonapi.core.model.DocumentData;
import com.kazforge.jsonapi.core.model.ErrorObject;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.core.model.JsonApiMembers;
import com.kazforge.jsonapi.core.model.JsonApiObject;
import com.kazforge.jsonapi.core.model.Link;
import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.Relationships;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.model.ResourceIdentity;
import com.kazforge.jsonapi.core.model.ResourceObject;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Aggregate document validation requiring full document context.
 *
 * <p>Call after constructing a {@link JsonApiDocument}. Local construction already enforces
 * single-value invariants; this validator covers identity uniqueness, full linkage,
 * local-identifier consistency, context-specific links, pagination cardinality, and
 * extension/profile member policy according to the supplied {@link ValidationContext}. Identity
 * uniqueness is representation-strict and alias-aware for identifier collections after
 * document-wide id↔lid binding.
 */
public final class JsonApiDocumentValidator {

  private static final String PATH_DATA = "/data";
  private static final String PATH_META = "/meta";
  private static final String PATH_LINKS = "/links";
  private static final String PATH_RELATIONSHIPS = "/relationships";

  public void validate(JsonApiDocument document, ValidationContext context) {
    validateAdditionalMembers(document.additionalMembers(), "", context);
    if (document.meta() != null) {
      validateMeta(document.meta(), PATH_META, context);
    }
    if (document.jsonapi() != null) {
      validateJsonApiObject(document.jsonapi(), context);
    }
    if (document.links() != null) {
      validateLinks(
          document.links(),
          PATH_LINKS,
          context.withLinksContext(LinksContext.TOP_LEVEL),
          null,
          null,
          document.data(),
          null);
    }
    if (document.errors() != null) {
      validateErrors(document.errors(), context);
    }
    if (document.data() == null) {
      if (context.documentUsage() == DocumentUsage.UPDATE_REQUEST) {
        throw new JsonApiValidationException(
            ValidationRuleCode.UPDATE_REQUIRES_SINGLE_RESOURCE,
            PATH_DATA,
            "Update request requires primary data as a single resource object");
      }
      if (context.documentUsage() == DocumentUsage.CREATE_REQUEST) {
        throw new JsonApiValidationException(
            ValidationRuleCode.CREATE_REQUIRES_SINGLE_RESOURCE,
            PATH_DATA,
            "Create request requires primary data as a single resource object");
      }
    } else {
      validatePrimaryData(document.data(), context);
    }
    if (document.included() != null || document.data() != null) {
      validateCompoundDocument(document.included(), document.data(), context);
    }
  }

  private void validateErrors(@Nullable List<ErrorObject> errors, ValidationContext context) {
    if (errors == null) {
      return;
    }
    for (int index = 0; index < errors.size(); index++) {
      validateError(errors.get(index), "/errors/" + index, context);
    }
  }

  private void validatePrimaryData(@Nullable DocumentData data, ValidationContext context) {
    if (data == null) {
      return;
    }
    if (context.documentUsage() == DocumentUsage.UPDATE_REQUEST
        && !(data instanceof DocumentData.SingleResource)) {
      throw new JsonApiValidationException(
          ValidationRuleCode.UPDATE_REQUIRES_SINGLE_RESOURCE,
          PATH_DATA,
          "Update request requires primary data as a single resource object");
    }
    if (context.documentUsage() == DocumentUsage.CREATE_REQUEST
        && !(data instanceof DocumentData.SingleResource)) {
      throw new JsonApiValidationException(
          ValidationRuleCode.CREATE_REQUIRES_SINGLE_RESOURCE,
          PATH_DATA,
          "Create request requires primary data as a single resource object");
    }
    switch (data) {
      case DocumentData.NullData ignored -> {
        // Explicit null primary data has no nested members to validate.
      }
      case DocumentData.SingleResource(ResourceObject resource) ->
          validateResource(resource, PATH_DATA, true, context);
      case DocumentData.ResourceCollection(List<ResourceObject> resources) -> {
        for (int index = 0; index < resources.size(); index++) {
          validateResource(resources.get(index), PATH_DATA + "/" + index, false, context);
        }
      }
      case DocumentData.SingleIdentifier(ResourceIdentifier identifier) ->
          validateIdentifier(identifier, PATH_DATA, context);
      case DocumentData.IdentifierCollection(List<ResourceIdentifier> identifiers) -> {
        ensureUniqueIdentifierIdentities(identifiers, PATH_DATA);
        for (int index = 0; index < identifiers.size(); index++) {
          validateIdentifier(identifiers.get(index), PATH_DATA + "/" + index, context);
        }
      }
    }
  }

  private void validateResource(
      ResourceObject resource, String path, boolean primary, ValidationContext context) {
    validateResourceIdentity(resource, path, context);
    validateUpdateEndpointIdentity(resource, path, primary, context);
    if (resource.attributes() != null) {
      validateAdditionalMembers(
          resource.attributes().additionalMembers(), path + "/attributes", context);
    }
    if (resource.relationships() != null) {
      validateResourceRelationships(resource, path, primary, context);
    }
    if (resource.links() != null) {
      validateLinks(
          resource.links(),
          path + PATH_LINKS,
          context.withLinksContext(LinksContext.RESOURCE),
          resource.type(),
          null,
          null,
          null);
    }
    if (resource.meta() != null) {
      validateMeta(resource.meta(), path + PATH_META, context);
    }
    validateAdditionalMembers(resource.additionalMembers(), path, context);
  }

  /**
   * Operation usages whose primary-resource relationships must carry replacement linkage {@code
   * data}. This is an operation-specific validation policy: links-only and meta-only relationships
   * remain valid general document representations outside these usages.
   */
  private static boolean isWriteRequestRequiringRelationshipData(ValidationContext context) {
    return context.documentUsage() == DocumentUsage.UPDATE_REQUEST
        || context.documentUsage() == DocumentUsage.CREATE_REQUEST;
  }

  private void validateResourceRelationships(
      ResourceObject resource, String path, boolean primary, ValidationContext context) {
    Relationships relationships = Objects.requireNonNull(resource.relationships());
    validateAdditionalMembers(
        relationships.additionalMembers(), path + PATH_RELATIONSHIPS, context);
    for (Map.Entry<String, Relationship> entry : relationships.relationships().entrySet()) {
      Relationship relationship = entry.getValue();
      if (isWriteRequestRequiringRelationshipData(context)
          && primary
          && !relationship.hasDataMember()) {
        throw new JsonApiValidationException(
            ValidationRuleCode.RELATIONSHIP_DATA_REQUIRED,
            JsonPointers.child(path + PATH_RELATIONSHIPS, entry.getKey()) + PATH_DATA,
            context.documentUsage() == DocumentUsage.CREATE_REQUEST
                ? "Create request relationship must contain data"
                : "Update request relationship must contain data");
      }
      validateRelationship(
          relationship,
          JsonPointers.child(path + PATH_RELATIONSHIPS, entry.getKey()),
          context,
          resource.type(),
          entry.getKey());
    }
  }

  private void validateRelationship(
      Relationship relationship,
      String path,
      ValidationContext context,
      String resourceType,
      String relationshipName) {
    if (relationship.data() != null) {
      validateRelationshipData(relationship.data(), path + PATH_DATA, context);
    }
    validateAdditionalMembers(relationship.additionalMembers(), path, context);
    // Qualify links-only relationships before link-context checks so non-qualifying
    // relations (e.g. alternate-only) report MISSING_RELATIONSHIP_MEMBER.
    validateLinksOnlyRelationshipQualification(relationship, path, context);
    if (relationship.links() != null) {
      validateLinks(
          relationship.links(),
          path + PATH_LINKS,
          context.withLinksContext(LinksContext.RELATIONSHIP),
          resourceType,
          relationshipName,
          null,
          relationship.data());
    }
    if (relationship.meta() != null) {
      validateMeta(relationship.meta(), path + PATH_META, context);
    }
  }

  private void validateLinksOnlyRelationshipQualification(
      Relationship relationship, String path, ValidationContext context) {
    if (relationship.data() != null || relationship.meta() != null) {
      return;
    }
    if (hasExtensionMemberName(relationship.additionalMembers())) {
      return;
    }
    Links links = relationship.links();
    if (links == null) {
      return;
    }
    for (String name : links.links().keySet()) {
      // Extension-shaped link keys defer to namespace policy in validateLinks.
      if (MemberNames.isExtensionMember(name)) {
        return;
      }
      if (isQualifyingRelationshipLink(name, context)) {
        return;
      }
    }
    throw new JsonApiValidationException(
        ValidationRuleCode.MISSING_RELATIONSHIP_MEMBER,
        path,
        "Links-only relationship must contain self, related, an allowed extension link,"
            + " or an allowed profile relation");
  }

  private static boolean hasExtensionMemberName(@Nullable Map<String, ?> members) {
    if (members == null || members.isEmpty()) {
      return false;
    }
    for (String name : members.keySet()) {
      if (MemberNames.isExtensionMember(name)) {
        return true;
      }
    }
    return false;
  }

  private boolean isQualifyingRelationshipLink(String name, ValidationContext context) {
    if (JsonApiMembers.SELF.equals(name) || JsonApiMembers.RELATED.equals(name)) {
      return true;
    }
    if (MemberNames.isExtensionMember(name)) {
      String namespace = name.substring(0, name.indexOf(':'));
      return context.allowedExtensionNamespaces().contains(namespace);
    }
    return context.allowedProfileMemberNames().contains(name);
  }

  private void validateRelationshipData(
      @Nullable RelationshipData data, String path, ValidationContext context) {
    if (data == null) {
      return;
    }
    switch (data) {
      case RelationshipData.NullLinkage ignored -> {
        // Explicit null to-one linkage has no identifiers to validate.
      }
      case RelationshipData.SingleLinkage(ResourceIdentifier identifier) ->
          validateIdentifier(identifier, path, context);
      case RelationshipData.IdentifierCollectionLinkage(List<ResourceIdentifier> identifiers) -> {
        ensureUniqueIdentifierIdentities(identifiers, path);
        for (int index = 0; index < identifiers.size(); index++) {
          validateIdentifier(identifiers.get(index), path + "/" + index, context);
        }
      }
    }
  }

  private void ensureUniqueIdentifierIdentities(List<ResourceIdentifier> identifiers, String path) {
    Set<ResourceIdentity> seen = new HashSet<>();
    for (int index = 0; index < identifiers.size(); index++) {
      ResourceIdentifier identifier = identifiers.get(index);
      if (identifier.hasId()) {
        ResourceIdentity id =
            ResourceIdentity.ofId(identifier.type(), Objects.requireNonNull(identifier.id()));
        if (!seen.add(id)) {
          throw duplicateIdentity(path + "/" + index, id);
        }
      }
      if (identifier.hasLid()) {
        ResourceIdentity lid =
            ResourceIdentity.ofLid(identifier.type(), Objects.requireNonNull(identifier.lid()));
        if (!seen.add(lid)) {
          throw duplicateIdentity(path + "/" + index, lid);
        }
      }
    }
  }

  /**
   * Rejects identifier-collection entries that resolve to the same canonical identity after
   * document-wide id↔lid binding. Runs before full-linkage early returns so documents without
   * {@code included} still receive the check.
   */
  private void ensureCanonicalUniqueIdentifierCollections(
      @Nullable DocumentData primaryData,
      @Nullable List<ResourceObject> included,
      IdentityRegistry registry) {
    if (primaryData instanceof DocumentData.IdentifierCollection(List<ResourceIdentifier> ids)) {
      ensureCanonicalUniqueIdentifierIdentities(ids, PATH_DATA, registry);
    }
    walkRelationshipIdentifierCollections(primaryData, included, registry);
  }

  private void walkRelationshipIdentifierCollections(
      @Nullable DocumentData primaryData,
      @Nullable List<ResourceObject> included,
      IdentityRegistry registry) {
    if (primaryData != null) {
      switch (primaryData) {
        case DocumentData.SingleResource(ResourceObject resource) ->
            ensureCanonicalUniqueFromResource(resource, PATH_DATA, registry);
        case DocumentData.ResourceCollection(List<ResourceObject> resources) -> {
          for (int index = 0; index < resources.size(); index++) {
            ensureCanonicalUniqueFromResource(
                resources.get(index), PATH_DATA + "/" + index, registry);
          }
        }
        default -> {
          // Identifier primary data has no relationship collections.
        }
      }
    }
    if (included == null) {
      return;
    }
    for (int index = 0; index < included.size(); index++) {
      ensureCanonicalUniqueFromResource(included.get(index), "/included/" + index, registry);
    }
  }

  private void ensureCanonicalUniqueFromResource(
      ResourceObject resource, String path, IdentityRegistry registry) {
    if (resource.relationships() == null) {
      return;
    }
    for (Map.Entry<String, Relationship> entry :
        resource.relationships().relationships().entrySet()) {
      RelationshipData data = entry.getValue().data();
      if (data
          instanceof RelationshipData.IdentifierCollectionLinkage(List<ResourceIdentifier> ids)) {
        String relPath = JsonPointers.child(path + PATH_RELATIONSHIPS, entry.getKey()) + PATH_DATA;
        ensureCanonicalUniqueIdentifierIdentities(ids, relPath, registry);
      }
    }
  }

  private void ensureCanonicalUniqueIdentifierIdentities(
      List<ResourceIdentifier> identifiers, String path, IdentityRegistry registry) {
    Set<ResourceIdentity> seen = new HashSet<>();
    for (int index = 0; index < identifiers.size(); index++) {
      ResourceIdentity canonical = canonicalIdentityFor(identifiers.get(index), registry);
      if (!seen.add(canonical)) {
        throw duplicateIdentity(path + "/" + index, canonical);
      }
    }
  }

  private static ResourceIdentity canonicalIdentityFor(
      ResourceIdentifier identifier, IdentityRegistry registry) {
    ResourceIdentity preferred = identifier.identityKey();
    ResourceIdentity canonical = registry.canonicalIdentity(preferred);
    if (canonical != null) {
      return canonical;
    }
    if (identifier.hasId() && identifier.hasLid()) {
      ResourceIdentity lid =
          ResourceIdentity.ofLid(identifier.type(), Objects.requireNonNull(identifier.lid()));
      ResourceIdentity viaLid = registry.canonicalIdentity(lid);
      if (viaLid != null) {
        return viaLid;
      }
    }
    return preferred;
  }

  private static JsonApiValidationException duplicateIdentity(String path, ResourceIdentity alias) {
    return new JsonApiValidationException(
        ValidationRuleCode.DUPLICATE_RESOURCE_IDENTITY,
        path,
        "Duplicate resource identity: " + alias);
  }

  private void validateIdentifier(
      ResourceIdentifier identifier, String path, ValidationContext context) {
    if (context.documentUsage() != DocumentUsage.CREATE_REQUEST && !identifier.hasId()) {
      throw new JsonApiValidationException(
          ValidationRuleCode.RESOURCE_ID_REQUIRED,
          path + "/id",
          "Resource identifier requires id outside create-request context");
    }
    if (identifier.meta() != null) {
      validateMeta(identifier.meta(), path + PATH_META, context);
    }
    validateAdditionalMembers(identifier.additionalMembers(), path, context);
  }

  private void validateResourceIdentity(
      ResourceObject resource, String path, ValidationContext context) {
    if (context.documentUsage() == DocumentUsage.CREATE_REQUEST) {
      return;
    }
    if (!resource.hasId()) {
      throw new JsonApiValidationException(
          ValidationRuleCode.RESOURCE_ID_REQUIRED,
          path + "/id",
          "Resource requires id outside create-request context");
    }
  }

  private void validateUpdateEndpointIdentity(
      ResourceObject resource, String path, boolean primary, ValidationContext context) {
    if (context.documentUsage() != DocumentUsage.UPDATE_REQUEST || !primary) {
      return;
    }
    EndpointIdentity expected = context.expectedEndpointIdentity();
    if (expected == null) {
      return;
    }
    if (!expected.type().equals(resource.type())) {
      throw new JsonApiValidationException(
          ValidationRuleCode.ENDPOINT_IDENTITY_MISMATCH,
          path + "/type",
          "Update resource type does not match the expected endpoint identity: " + expected.type());
    }
    if (!expected.id().equals(Objects.requireNonNull(resource.id()))) {
      throw new JsonApiValidationException(
          ValidationRuleCode.ENDPOINT_IDENTITY_MISMATCH,
          path + "/id",
          "Update resource id does not match the expected endpoint identity: " + expected.id());
    }
  }

  private void validateError(ErrorObject error, String path, ValidationContext context) {
    if (error.links() != null) {
      validateLinks(
          error.links(),
          path + PATH_LINKS,
          context.withLinksContext(LinksContext.ERROR),
          null,
          null,
          null,
          null);
    }
    if (error.source() != null) {
      validateAdditionalMembers(error.source().additionalMembers(), path + "/source", context);
    }
    if (error.meta() != null) {
      validateMeta(error.meta(), path + PATH_META, context);
    }
    validateAdditionalMembers(error.additionalMembers(), path, context);
  }

  private void validateMeta(@Nullable Meta meta, String path, ValidationContext context) {
    if (meta == null) {
      return;
    }
    for (String name : meta.members().keySet()) {
      if (!MemberNames.isExtensionMember(name)) {
        continue;
      }
      String namespace = name.substring(0, name.indexOf(':'));
      if (!context.allowedExtensionNamespaces().contains(namespace)) {
        throw new JsonApiValidationException(
            ValidationRuleCode.DISALLOWED_ADDITIONAL_MEMBER,
            JsonPointers.child(path, name),
            "Extension namespace not allowed: " + namespace);
      }
    }
  }

  private void validateJsonApiObject(@Nullable JsonApiObject jsonapi, ValidationContext context) {
    if (jsonapi == null) {
      return;
    }
    if (jsonapi.profile() != null) {
      validateProfileUris(jsonapi.profile(), context);
    }
    if (jsonapi.meta() != null) {
      validateMeta(jsonapi.meta(), "/jsonapi" + PATH_META, context);
    }
    validateAdditionalMembers(jsonapi.additionalMembers(), "/jsonapi", context);
  }

  private void validateProfileUris(@Nullable List<String> profile, ValidationContext context) {
    if (profile == null || context.allowedProfileUris().isEmpty()) {
      return;
    }
    for (int i = 0; i < profile.size(); i++) {
      String uri = profile.get(i);
      if (!context.allowedProfileUris().contains(uri)) {
        throw new JsonApiValidationException(
            ValidationRuleCode.DISALLOWED_ADDITIONAL_MEMBER,
            "/jsonapi/profile/" + i,
            "Profile URI not allowed: " + uri);
      }
    }
  }

  private void validateCompoundDocument(
      @Nullable List<ResourceObject> included,
      @Nullable DocumentData primaryData,
      ValidationContext context) {
    IdentityRegistry registry = new IdentityRegistry();
    registerPrimaryResources(primaryData, registry);
    registerLinkageIdentifiers(primaryData, registry);
    if (included != null) {
      for (int index = 0; index < included.size(); index++) {
        ResourceObject resource = included.get(index);
        String path = "/included/" + index;
        validateResource(resource, path, false, context);
        registerIncludedResource(resource, path, registry);
        registerLinkageFromResource(resource, path, registry);
      }
    }
    ensureCanonicalUniqueIdentifierCollections(primaryData, included, registry);
    if (included == null) {
      return;
    }
    Set<ResourceIdentity> linked =
        collectLinkedIdentities(primaryData, registry, context.sparseFieldsetLinkageExemptions());
    for (ResourceIdentity identity : registry.includedIdentities()) {
      if (!linked.contains(identity)) {
        throw new JsonApiValidationException(
            ValidationRuleCode.FULL_LINKAGE_VIOLATION,
            "/included",
            "Included resource lacks full linkage: " + identity);
      }
    }
  }

  private void registerLinkageIdentifiers(@Nullable DocumentData data, IdentityRegistry registry) {
    if (data == null) {
      return;
    }
    switch (data) {
      case DocumentData.NullData ignored -> {
        // No linkage.
      }
      case DocumentData.SingleResource(ResourceObject resource) ->
          registerLinkageFromResource(resource, PATH_DATA, registry);
      case DocumentData.ResourceCollection(List<ResourceObject> resources) -> {
        for (int index = 0; index < resources.size(); index++) {
          registerLinkageFromResource(resources.get(index), PATH_DATA + "/" + index, registry);
        }
      }
      case DocumentData.SingleIdentifier(ResourceIdentifier identifier) ->
          registry.registerIdentifier(identifier, PATH_DATA);
      case DocumentData.IdentifierCollection(List<ResourceIdentifier> identifiers) -> {
        for (int index = 0; index < identifiers.size(); index++) {
          registry.registerIdentifier(identifiers.get(index), PATH_DATA + "/" + index);
        }
      }
    }
  }

  private void registerLinkageFromResource(
      ResourceObject resource, String path, IdentityRegistry registry) {
    if (resource.relationships() == null) {
      return;
    }
    for (Map.Entry<String, Relationship> entry :
        resource.relationships().relationships().entrySet()) {
      Relationship relationship = entry.getValue();
      if (relationship.data() == null) {
        continue;
      }
      String relPath = JsonPointers.child(path + PATH_RELATIONSHIPS, entry.getKey()) + PATH_DATA;
      registerLinkageFromRelationshipData(relationship.data(), relPath, registry);
    }
  }

  private void registerLinkageFromRelationshipData(
      @Nullable RelationshipData data, String path, IdentityRegistry registry) {
    if (data == null) {
      return;
    }
    switch (data) {
      case RelationshipData.NullLinkage ignored -> {
        // No identifiers.
      }
      case RelationshipData.SingleLinkage(ResourceIdentifier identifier) ->
          registry.registerIdentifier(identifier, path);
      case RelationshipData.IdentifierCollectionLinkage(List<ResourceIdentifier> identifiers) -> {
        for (int index = 0; index < identifiers.size(); index++) {
          registry.registerIdentifier(identifiers.get(index), path + "/" + index);
        }
      }
    }
  }

  private void registerPrimaryResources(@Nullable DocumentData data, IdentityRegistry registry) {
    if (data == null) {
      return;
    }
    switch (data) {
      case DocumentData.NullData ignored -> {
        // No primary resources.
      }
      case DocumentData.SingleResource(ResourceObject resource) ->
          registry.registerPrimary(resource, PATH_DATA);
      case DocumentData.ResourceCollection(List<ResourceObject> resources) -> {
        for (int index = 0; index < resources.size(); index++) {
          registry.registerPrimary(resources.get(index), PATH_DATA + "/" + index);
        }
      }
      case DocumentData.SingleIdentifier(ResourceIdentifier identifier) ->
          registry.registerIdentifier(identifier, PATH_DATA);
      case DocumentData.IdentifierCollection(List<ResourceIdentifier> identifiers) -> {
        for (int index = 0; index < identifiers.size(); index++) {
          registry.registerIdentifier(identifiers.get(index), PATH_DATA + "/" + index);
        }
      }
    }
  }

  private void registerIncludedResource(
      ResourceObject resource, String path, IdentityRegistry registry) {
    if (!resource.hasId() && !resource.hasLid()) {
      throw new JsonApiValidationException(
          ValidationRuleCode.INCLUDED_RESOURCE_IDENTITY_REQUIRED,
          path,
          "Included resource requires id or lid");
    }
    registry.registerIncluded(resource, path);
  }

  private Set<ResourceIdentity> collectLinkedIdentities(
      @Nullable DocumentData primaryData,
      IdentityRegistry registry,
      Set<ResourceIdentity> linkageExemptions) {
    Set<ResourceIdentity> linked = new HashSet<>();
    Queue<ResourceIdentity> queue = new ArrayDeque<>();
    // Sparse-fieldset exemptions are legitimate linkage roots: seeding them keeps their own
    // outbound relationships reachable so exempted subtrees stay valid while unrelated included
    // resources still require full linkage.
    for (ResourceIdentity exemption : linkageExemptions) {
      addLinked(exemption, linked, queue, registry);
    }
    collectFromPrimaryData(primaryData, linked, queue, registry);
    while (!queue.isEmpty()) {
      expandIncludedRelationships(queue.poll(), linked, queue, registry);
    }
    return linked;
  }

  private void expandIncludedRelationships(
      ResourceIdentity identity,
      Set<ResourceIdentity> linked,
      Queue<ResourceIdentity> queue,
      IdentityRegistry registry) {
    ResourceObject included = registry.resourceFor(identity);
    if (included == null || included.relationships() == null) {
      return;
    }
    for (Relationship relationship : included.relationships().relationships().values()) {
      if (relationship.data() != null) {
        collectFromRelationshipData(relationship.data(), linked, queue, registry);
      }
    }
  }

  private void collectFromPrimaryData(
      @Nullable DocumentData data,
      Set<ResourceIdentity> linked,
      Queue<ResourceIdentity> queue,
      IdentityRegistry registry) {
    if (data == null) {
      return;
    }
    switch (data) {
      case DocumentData.NullData ignored -> {
        // No linkage from explicit null primary data.
      }
      case DocumentData.SingleResource(ResourceObject resource) ->
          collectFromResource(resource, linked, queue, registry);
      case DocumentData.ResourceCollection(List<ResourceObject> resources) -> {
        for (ResourceObject resource : resources) {
          collectFromResource(resource, linked, queue, registry);
        }
      }
      case DocumentData.SingleIdentifier(ResourceIdentifier identifier) ->
          addLinked(identifier.identityKey(), linked, queue, registry);
      case DocumentData.IdentifierCollection(List<ResourceIdentifier> identifiers) -> {
        for (ResourceIdentifier identifier : identifiers) {
          addLinked(identifier.identityKey(), linked, queue, registry);
        }
      }
    }
  }

  private void collectFromResource(
      ResourceObject resource,
      Set<ResourceIdentity> linked,
      Queue<ResourceIdentity> queue,
      IdentityRegistry registry) {
    addLinked(resource.identityKey(), linked, queue, registry);
    if (resource.hasId() && resource.hasLid()) {
      addLinked(
          ResourceIdentity.ofLid(resource.type(), Objects.requireNonNull(resource.lid())),
          linked,
          queue,
          registry);
    }
    if (resource.relationships() == null) {
      return;
    }
    for (Relationship relationship : resource.relationships().relationships().values()) {
      if (relationship.data() != null) {
        collectFromRelationshipData(relationship.data(), linked, queue, registry);
      }
    }
  }

  private void collectFromRelationshipData(
      @Nullable RelationshipData data,
      Set<ResourceIdentity> linked,
      Queue<ResourceIdentity> queue,
      IdentityRegistry registry) {
    if (data == null) {
      return;
    }
    switch (data) {
      case RelationshipData.NullLinkage ignored -> {
        // Explicit null linkage contributes no identifiers.
      }
      case RelationshipData.SingleLinkage(ResourceIdentifier identifier) ->
          addLinked(identifier.identityKey(), linked, queue, registry);
      case RelationshipData.IdentifierCollectionLinkage(List<ResourceIdentifier> identifiers) -> {
        for (ResourceIdentifier identifier : identifiers) {
          addLinked(identifier.identityKey(), linked, queue, registry);
        }
      }
    }
  }

  private void addLinked(
      @Nullable ResourceIdentity identity,
      Set<ResourceIdentity> linked,
      Queue<ResourceIdentity> queue,
      IdentityRegistry registry) {
    if (identity == null) {
      return;
    }
    ResourceIdentity canonical = registry.canonicalIdentity(identity);
    ResourceIdentity key = canonical != null ? canonical : identity;
    if (linked.add(key)) {
      queue.add(key);
      linked.addAll(registry.aliasesOf(key));
    }
  }

  private void validateLinks(
      @Nullable Links links,
      String path,
      ValidationContext context,
      @Nullable String resourceType,
      @Nullable String relationshipName,
      @Nullable DocumentData primaryData,
      @Nullable RelationshipData relationshipData) {
    if (links == null) {
      return;
    }
    validateAdditionalMembers(links.additionalMembers(), path, context);
    for (Map.Entry<String, @Nullable Link> entry : links.links().entrySet()) {
      validateLinkValue(entry.getValue(), JsonPointers.child(path, entry.getKey()), context);
      validateLinkEntry(
          entry.getKey(),
          path,
          context,
          resourceType,
          relationshipName,
          primaryData,
          relationshipData);
    }
  }

  private void validateLinkValue(@Nullable Link link, String path, ValidationContext context) {
    if (!(link instanceof Link.ObjectLink objectLink)) {
      return;
    }
    validateAdditionalMembers(objectLink.additionalMembers(), path, context);
    if (objectLink.meta() != null) {
      validateMeta(objectLink.meta(), path + PATH_META, context);
    }
    if (objectLink.describedby() != null) {
      validateLinkValue(
          objectLink.describedby(), JsonPointers.child(path, JsonApiMembers.DESCRIBEDBY), context);
    }
  }

  private void validateLinkEntry(
      String name,
      String path,
      ValidationContext context,
      @Nullable String resourceType,
      @Nullable String relationshipName,
      @Nullable DocumentData primaryData,
      @Nullable RelationshipData relationshipData) {
    if (MemberNames.isExtensionMember(name)) {
      String namespace = name.substring(0, name.indexOf(':'));
      if (!context.allowedExtensionNamespaces().contains(namespace)) {
        throw new JsonApiValidationException(
            ValidationRuleCode.DISALLOWED_ADDITIONAL_MEMBER,
            JsonPointers.child(path, name),
            "Extension namespace not allowed: " + namespace);
      }
    } else {
      if (!SyntaxValidators.isValidLinkRelation(name)) {
        throw new JsonApiValidationException(
            ValidationRuleCode.INVALID_LINK_RELATION,
            JsonPointers.child(path, name),
            "Invalid link relation name: " + name);
      }
      if (!isAllowedLinkName(name, context)) {
        throw new JsonApiValidationException(
            ValidationRuleCode.INVALID_LINKS_CONTEXT,
            JsonPointers.child(path, name),
            "Non-standard link in context " + context.linksContext() + ": " + name);
      }
    }
    if (!JsonApiMembers.PAGINATION_LINKS.contains(name)) {
      return;
    }
    validatePaginationLink(
        name, path, context, resourceType, relationshipName, primaryData, relationshipData);
  }

  private void validatePaginationLink(
      String name,
      String path,
      ValidationContext context,
      @Nullable String resourceType,
      @Nullable String relationshipName,
      @Nullable DocumentData primaryData,
      @Nullable RelationshipData relationshipData) {
    if (context.linksContext() == LinksContext.TOP_LEVEL) {
      if (!isCollectionPrimaryData(primaryData)) {
        throw new JsonApiValidationException(
            ValidationRuleCode.PAGINATION_REQUIRES_COLLECTION,
            JsonPointers.child(path, name),
            "Top-level pagination link requires collection primary data");
      }
      return;
    }
    if (context.linksContext() != LinksContext.RELATIONSHIP || relationshipName == null) {
      return;
    }
    if (relationshipData != null) {
      switch (relationshipData) {
        case RelationshipData.IdentifierCollectionLinkage ignored -> {
          // Proven to-many: pagination allowed.
        }
        case RelationshipData.NullLinkage ignored ->
            throw paginationRequiresCollection(path, name, relationshipName);
        case RelationshipData.SingleLinkage ignored ->
            throw paginationRequiresCollection(path, name, relationshipName);
      }
      return;
    }
    if (resourceType != null
        && context.relationshipPaginationHint(resourceType, relationshipName).orElse(null)
            == RelationshipCardinality.TO_ONE) {
      throw paginationRequiresCollection(path, name, relationshipName);
    }
  }

  private static JsonApiValidationException paginationRequiresCollection(
      String path, String name, String relationshipName) {
    return new JsonApiValidationException(
        ValidationRuleCode.PAGINATION_REQUIRES_COLLECTION,
        JsonPointers.child(path, name),
        "Relationship pagination requires collection linkage: " + relationshipName);
  }

  private static boolean isCollectionPrimaryData(@Nullable DocumentData data) {
    return data instanceof DocumentData.ResourceCollection
        || data instanceof DocumentData.IdentifierCollection;
  }

  private boolean isAllowedLinkName(String name, ValidationContext context) {
    return Links.standardMembers(context.linksContext()).contains(name)
        || context.allowedProfileMemberNames().contains(name);
  }

  private void validateAdditionalMembers(
      Map<String, @Nullable Object> members, String basePath, ValidationContext context) {
    for (Map.Entry<String, @Nullable Object> entry : members.entrySet()) {
      validateAdditionalMember(entry.getKey(), basePath, context);
    }
  }

  private void validateAdditionalMember(String name, String basePath, ValidationContext context) {
    if (MemberNames.isAtMember(name)) {
      return;
    }
    if (MemberNames.isExtensionMember(name)) {
      String namespace = name.substring(0, name.indexOf(':'));
      if (!context.allowedExtensionNamespaces().contains(namespace)) {
        throw new JsonApiValidationException(
            ValidationRuleCode.DISALLOWED_ADDITIONAL_MEMBER,
            JsonPointers.child(basePath, name),
            "Extension namespace not allowed: " + namespace);
      }
      return;
    }
    if (!context.allowedProfileMemberNames().contains(name)) {
      throw new JsonApiValidationException(
          ValidationRuleCode.UNKNOWN_ADDITIONAL_MEMBER,
          JsonPointers.child(basePath, name),
          "Unknown unnamespaced member: " + name);
    }
  }

  /** Document-wide identity index with one-to-one id↔lid partners and shared canonical aliases. */
  private static final class IdentityRegistry {
    private final Map<ResourceIdentity, ResourceObject> byAlias = new HashMap<>();
    private final Map<ResourceIdentity, ResourceIdentity> canonicalByAlias = new HashMap<>();
    private final Map<ResourceIdentity, Set<ResourceIdentity>> aliasesByCanonical = new HashMap<>();
    private final Map<ResourceIdentity, ResourceIdentity> idToLid = new HashMap<>();
    private final Map<ResourceIdentity, ResourceIdentity> lidToId = new HashMap<>();
    private final Set<ResourceIdentity> includedCanonical = new HashSet<>();

    void registerPrimary(ResourceObject resource, String path) {
      registerResource(resource, path, false);
    }

    void registerIncluded(ResourceObject resource, String path) {
      registerResource(resource, path, true);
    }

    void registerIdentifier(ResourceIdentifier identifier, String path) {
      if (identifier.hasId() && identifier.hasLid()) {
        bindIdLidPair(
            ResourceIdentity.ofId(identifier.type(), Objects.requireNonNull(identifier.id())),
            ResourceIdentity.ofLid(identifier.type(), Objects.requireNonNull(identifier.lid())),
            path);
      } else if (identifier.hasLid()) {
        ResourceIdentity lid =
            ResourceIdentity.ofLid(identifier.type(), Objects.requireNonNull(identifier.lid()));
        if (!canonicalByAlias.containsKey(lid)) {
          addAlias(lid, lid);
        }
      }
    }

    private void registerResource(ResourceObject resource, String path, boolean included) {
      ResourceIdentity idAlias =
          resource.hasId()
              ? ResourceIdentity.ofId(resource.type(), Objects.requireNonNull(resource.id()))
              : null;
      ResourceIdentity lidAlias =
          resource.hasLid()
              ? ResourceIdentity.ofLid(resource.type(), Objects.requireNonNull(resource.lid()))
              : null;
      if (idAlias == null && lidAlias == null) {
        return;
      }

      if (idAlias != null && lidAlias != null) {
        bindIdLidPair(idAlias, lidAlias, path);
      } else if (lidAlias != null && !canonicalByAlias.containsKey(lidAlias)) {
        addAlias(lidAlias, lidAlias);
      }

      ResourceIdentity canonical =
          Objects.requireNonNullElseGet(
              idAlias, () -> lidToId.getOrDefault(Objects.requireNonNull(lidAlias), lidAlias));

      ensureNoDuplicateOnCanonical(canonical, resource, path);

      if (idAlias != null) {
        putResource(idAlias, resource, path);
        addAlias(idAlias, canonical);
      }
      if (lidAlias != null) {
        putResource(lidAlias, resource, path);
        addAlias(lidAlias, canonical);
      }
      // Lid-only resources whose lid was pre-bound to an id must also be
      // reachable under the canonical id for full-linkage traversal.
      ensureResourceUnderCanonical(canonical, resource, path);
      if (included) {
        includedCanonical.add(canonicalByAlias.getOrDefault(canonical, canonical));
      }
    }

    private void ensureResourceUnderCanonical(
        ResourceIdentity canonical, ResourceObject resource, String path) {
      addAlias(canonical, canonical);
      ResourceObject existing = byAlias.putIfAbsent(canonical, resource);
      if (existing != null && !existing.equals(resource)) {
        throw duplicate(path, canonical);
      }
    }

    private void ensureNoDuplicateOnCanonical(
        ResourceIdentity canonical, ResourceObject resource, String path) {
      ResourceObject existing = resourceFor(canonical);
      if (existing != null && !existing.equals(resource)) {
        throw duplicate(path, canonical);
      }
      for (ResourceIdentity alias : aliasesByCanonical.getOrDefault(canonical, Set.of())) {
        ResourceObject underAlias = byAlias.get(alias);
        if (underAlias != null && !underAlias.equals(resource)) {
          throw duplicate(path, canonical);
        }
      }
    }

    private void putResource(ResourceIdentity alias, ResourceObject resource, String path) {
      ResourceObject previous = byAlias.put(alias, resource);
      if (previous == null) {
        return;
      }
      if (alias.isLid() && !previous.equals(resource)) {
        throw inconsistent(path, alias);
      }
      throw duplicate(path, alias);
    }

    private void bindIdLidPair(ResourceIdentity idAlias, ResourceIdentity lidAlias, String path) {
      ResourceIdentity existingLidForId = idToLid.get(idAlias);
      ResourceIdentity existingIdForLid = lidToId.get(lidAlias);
      if (existingLidForId != null && !existingLidForId.equals(lidAlias)) {
        throw inconsistent(path, lidAlias);
      }
      if (existingIdForLid != null && !existingIdForLid.equals(idAlias)) {
        throw inconsistent(path, lidAlias);
      }

      ResourceIdentity lidCanonical = canonicalByAlias.get(lidAlias);
      if (lidCanonical != null && lidCanonical.isId() && !lidCanonical.equals(idAlias)) {
        throw inconsistent(path, lidAlias);
      }
      ResourceIdentity idCanonical = canonicalByAlias.get(idAlias);
      if (idCanonical != null && idCanonical.isId() && !idCanonical.equals(idAlias)) {
        throw inconsistent(path, idAlias);
      }

      ResourceObject underLid = byAlias.get(lidAlias);
      ResourceObject underId = byAlias.get(idAlias);
      if (underLid != null && underId != null && !underLid.equals(underId)) {
        throw duplicate(path, idAlias);
      }

      idToLid.put(idAlias, lidAlias);
      lidToId.put(lidAlias, idAlias);

      if (lidCanonical != null && lidCanonical.equals(lidAlias)) {
        remapProvisionalLid(lidAlias, idAlias, path);
      }
      addAlias(idAlias, idAlias);
      addAlias(lidAlias, idAlias);
    }

    private void addAlias(ResourceIdentity alias, ResourceIdentity canonical) {
      canonicalByAlias.put(alias, canonical);
      aliasesByCanonical.computeIfAbsent(canonical, ignored -> new HashSet<>()).add(alias);
    }

    private void remapProvisionalLid(ResourceIdentity from, ResourceIdentity to, String path) {
      if (from.equals(to)) {
        return;
      }
      Set<ResourceIdentity> aliases = aliasesByCanonical.remove(from);
      if (aliases == null) {
        return;
      }
      Set<ResourceIdentity> target =
          aliasesByCanonical.computeIfAbsent(to, ignored -> new HashSet<>());
      for (ResourceIdentity alias : aliases) {
        canonicalByAlias.put(alias, to);
        target.add(alias);
      }
      ResourceObject fromResource = byAlias.get(from);
      ResourceObject toResource = byAlias.get(to);
      if (fromResource != null && toResource != null && !fromResource.equals(toResource)) {
        throw duplicate(path, to);
      }
      if (fromResource != null) {
        byAlias.putIfAbsent(to, fromResource);
      }
      if (includedCanonical.remove(from)) {
        includedCanonical.add(to);
      }
    }

    private static JsonApiValidationException inconsistent(String path, ResourceIdentity alias) {
      return new JsonApiValidationException(
          ValidationRuleCode.INCONSISTENT_LOCAL_IDENTIFIER,
          path,
          "Inconsistent local identifier: " + alias);
    }

    private static JsonApiValidationException duplicate(String path, ResourceIdentity alias) {
      return new JsonApiValidationException(
          ValidationRuleCode.DUPLICATE_RESOURCE_IDENTITY,
          path,
          "Duplicate resource identity: " + alias);
    }

    @Nullable ResourceIdentity canonicalIdentity(ResourceIdentity alias) {
      return canonicalByAlias.get(alias);
    }

    @Nullable ResourceObject resourceFor(ResourceIdentity identity) {
      ResourceIdentity canonical = canonicalByAlias.getOrDefault(identity, identity);
      ResourceObject direct = byAlias.get(canonical);
      if (direct != null) {
        return direct;
      }
      direct = byAlias.get(identity);
      if (direct != null) {
        return direct;
      }
      for (ResourceIdentity alias : aliasesByCanonical.getOrDefault(canonical, Set.of())) {
        ResourceObject underAlias = byAlias.get(alias);
        if (underAlias != null) {
          return underAlias;
        }
      }
      return null;
    }

    Set<ResourceIdentity> aliasesOf(ResourceIdentity canonical) {
      return aliasesByCanonical.getOrDefault(canonical, Set.of());
    }

    Set<ResourceIdentity> includedIdentities() {
      return includedCanonical;
    }
  }
}
