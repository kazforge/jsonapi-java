package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.JsonApiMembers;
import com.kazforge.jsonapi.core.validation.MemberNames;
import com.kazforge.jsonapi.diagnostic.JsonApiMappingException;
import com.kazforge.jsonapi.diagnostic.MappingDiagnostic;
import com.kazforge.jsonapi.diagnostic.MappingLocation;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Backend-neutral resource-level mapping-definition invariants shared by backend {@code
 * MappingDefinitionResolver}s.
 *
 * <p>The configured backend remains the authority for property discovery, role-annotation
 * harvesting, effective-property resolution, and adapter-local declaration checks. This type owns
 * only the invariants that are independent of the backend once a role and its JSON:API member name
 * are known: resource-type name validity, per-property JSON:API member-name validity, relationship
 * meta target matching by logical identity, and the resource-level role rules (single identity
 * role, duplicate member names, attribute/relationship collisions, and single resource meta).
 * Adapters keep their own unresolved relationship-meta records and rebuild their native property
 * records from the returned {@link SemanticProperty} metadata.
 *
 * <p>Diagnostics and their message text are part of this shared contract, so both backends report
 * identical codes, resource classes, resource-relative locations, and messages. The methods never
 * touch annotation types; adapters pass already-harvested names.
 *
 * <p>This type is unsupported implementation detail for backend cooperation, not consumer SPI, and
 * must not appear in supported backend public signatures.
 */
@NullMarked
public final class MappingDefinitionInvariants {

  private MappingDefinitionInvariants() {}

  /**
   * Validates a class-level resource type name.
   *
   * @throws JsonApiMappingException {@link MappingDiagnostic#MISSING_RESOURCE_ANNOTATION} when the
   *     name is absent, or {@link MappingDiagnostic#INVALID_RESOURCE_TYPE} when it is empty or not
   *     a valid JSON:API member name
   */
  public static String requireResourceTypeName(
      @Nullable String resourceTypeName, Class<?> rawType) {
    if (resourceTypeName == null) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.MISSING_RESOURCE_ANNOTATION,
          rawType,
          "Missing @JsonApiResource on " + rawType.getName());
    }
    if (resourceTypeName.isEmpty()) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.INVALID_RESOURCE_TYPE,
          rawType,
          "@JsonApiResource.type() must not be empty on " + rawType.getName());
    }
    if (!MemberNames.isValid(resourceTypeName)) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.INVALID_RESOURCE_TYPE,
          rawType,
          "Invalid resource type name: " + resourceTypeName);
    }
    return resourceTypeName;
  }

  /**
   * Validates one classified property's JSON:API member name against its role. Attributes and
   * relationships must not be empty or reserved member names; a relationship-meta target identity
   * must not be empty.
   *
   * @throws JsonApiMappingException {@link MappingDiagnostic#INVALID_ATTRIBUTE_NAME}, {@link
   *     MappingDiagnostic#INVALID_RELATIONSHIP_NAME}, or {@link
   *     MappingDiagnostic#INVALID_RELATIONSHIP_META_TARGET}
   */
  public static void validateJsonApiName(
      String jsonapiName, PropertyRole role, String logicalName, Class<?> rawType) {
    if (role == PropertyRole.RELATIONSHIP_META) {
      if (jsonapiName.isEmpty()) {
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.INVALID_RELATIONSHIP_META_TARGET,
            rawType,
            "@JsonApiRelationshipMeta.relationship() must not be empty for property '"
                + logicalName
                + "'");
      }
      return;
    }
    MappingDiagnostic diagnostic =
        switch (role) {
          case ATTRIBUTE -> MappingDiagnostic.INVALID_ATTRIBUTE_NAME;
          case RELATIONSHIP -> MappingDiagnostic.INVALID_RELATIONSHIP_NAME;
          default -> null;
        };
    if (diagnostic == null) {
      return;
    }
    if (jsonapiName.isEmpty() || isForbiddenMemberName(jsonapiName)) {
      // The wire name itself is invalid, so it cannot form a pointer segment; the offending name
      // and its logical property stay in the message.
      throw JsonApiMappingException.withoutLocation(
          diagnostic,
          rawType,
          "Invalid JSON:API member name '" + jsonapiName + "' for property '" + logicalName + "'");
    }
  }

  /**
   * Validates the resource-level role rules over already-classified property carriers, in the
   * backend-neutral order: identity role, duplicate attribute names, duplicate relationship names,
   * attribute/relationship collisions, then single resource meta.
   *
   * @throws JsonApiMappingException {@link MappingDiagnostic#DUPLICATE_ROLE}, {@link
   *     MappingDiagnostic#MISSING_IDENTIFIER}, or {@link MappingDiagnostic#NAME_COLLISION}
   */
  public static void validatePropertyRoles(
      List<? extends SemanticPropertyCarrier> identifierProperties,
      List<? extends SemanticPropertyCarrier> localIdProperties,
      List<? extends SemanticPropertyCarrier> attributeProperties,
      List<? extends SemanticPropertyCarrier> relationshipProperties,
      List<? extends SemanticPropertyCarrier> resourceMetaProperties,
      Class<?> rawType) {
    requireSingleIdentityRole(identifierProperties, localIdProperties, rawType);
    rejectDuplicateNames(
        attributeProperties, rawType, "attribute", MappingDefinitionInvariants::attributeLocation);
    rejectDuplicateNames(
        relationshipProperties,
        rawType,
        "relationship",
        MappingDefinitionInvariants::relationshipLocation);
    rejectAttributeRelationshipCollisions(attributeProperties, relationshipProperties, rawType);
    requireSingleResourceMeta(resourceMetaProperties, rawType);
    // Relationship-meta target validation already ran in the backend's binding step
    // (bindWriteRelationshipMeta during classifyProperties and bindReadRelationshipMeta during
    // resolveRead): each binding rejects unknown targets and duplicate target identities (by the
    // relationship property's logical name) and rewrites each meta property's jsonapiName to the
    // target relationship's wire name, so no post-binding target check remains needed.
  }

  /**
   * Binds one unresolved relationship-meta declaration to its target relationship by logical
   * identity and returns the resolved {@link PropertyRole#RELATIONSHIP_META} metadata carrying the
   * target's JSON:API member name. {@code seenTargets} accumulates the logical identities already
   * targeted across the same binding pass so a second meta property for one relationship fails.
   *
   * @throws JsonApiMappingException {@link MappingDiagnostic#UNRESOLVED_RELATIONSHIP_META} for an
   *     unknown target, or {@link MappingDiagnostic#DUPLICATE_ROLE} for a duplicate target
   */
  public static SemanticProperty resolveRelationshipMeta(
      String metaLogicalName,
      String metaExternalName,
      String targetIdentity,
      List<? extends SemanticPropertyCarrier> relationshipProperties,
      Set<String> seenTargets,
      Class<?> rawType) {
    SemanticPropertyCarrier target =
        relationshipByIdentity(relationshipProperties).get(targetIdentity);
    if (target == null) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.UNRESOLVED_RELATIONSHIP_META,
          rawType,
          "@JsonApiRelationshipMeta for property '"
              + metaLogicalName
              + "' references unknown relationship '"
              + targetIdentity
              + "' on "
              + rawType.getName());
    }
    if (!seenTargets.add(targetIdentity)) {
      throw new JsonApiMappingException(
          MappingDiagnostic.DUPLICATE_ROLE,
          rawType,
          relationshipMetaLocation(target.jsonapiName()),
          "Multiple relationship meta properties target relationship '"
              + targetIdentity
              + "' on "
              + rawType.getName()
              + "; at most one is allowed");
    }
    return new SemanticProperty(
        PropertyRole.RELATIONSHIP_META, metaLogicalName, metaExternalName, target.jsonapiName());
  }

  /** Resource-relative diagnostic location for an attribute's member. */
  public static MappingLocation attributeLocation(String jsonapiName) {
    return MappingLocation.of(JsonApiMembers.ATTRIBUTES, jsonapiName);
  }

  /** Resource-relative diagnostic location for a relationship's linkage member. */
  public static MappingLocation relationshipLocation(String jsonapiName) {
    return MappingLocation.of(JsonApiMembers.RELATIONSHIPS, jsonapiName, JsonApiMembers.DATA);
  }

  private static MappingLocation resourceMetaLocation() {
    return MappingLocation.of(JsonApiMembers.META);
  }

  private static MappingLocation relationshipMetaLocation(String relationshipName) {
    return MappingLocation.of(JsonApiMembers.RELATIONSHIPS, relationshipName, JsonApiMembers.META);
  }

  private static <P extends SemanticPropertyCarrier> Map<String, P> relationshipByIdentity(
      List<? extends P> relationshipProperties) {
    Map<String, P> byIdentity = new LinkedHashMap<>();
    for (P relationship : relationshipProperties) {
      byIdentity.put(relationship.logicalName(), relationship);
    }
    return byIdentity;
  }

  private static boolean isForbiddenMemberName(String jsonapiName) {
    return !MemberNames.isValid(jsonapiName)
        || JsonApiMembers.ID.equals(jsonapiName)
        || JsonApiMembers.TYPE.equals(jsonapiName);
  }

  private static void requireSingleIdentityRole(
      List<? extends SemanticPropertyCarrier> identifierProperties,
      List<? extends SemanticPropertyCarrier> localIdProperties,
      Class<?> rawType) {
    if (identifierProperties.size() > 1) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.DUPLICATE_ROLE,
          rawType,
          "Multiple id properties found for " + rawType.getName());
    }
    if (localIdProperties.size() > 1) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.DUPLICATE_ROLE,
          rawType,
          "Multiple local-id properties found for " + rawType.getName());
    }
    if (identifierProperties.isEmpty() && localIdProperties.isEmpty()) {
      throw JsonApiMappingException.withoutLocation(
          MappingDiagnostic.MISSING_IDENTIFIER,
          rawType,
          "No id or local-id property found for " + rawType.getName());
    }
  }

  private static void requireSingleResourceMeta(
      List<? extends SemanticPropertyCarrier> resourceMetaProperties, Class<?> rawType) {
    if (resourceMetaProperties.size() > 1) {
      throw new JsonApiMappingException(
          MappingDiagnostic.DUPLICATE_ROLE,
          rawType,
          resourceMetaLocation(),
          "Multiple resource meta properties found for "
              + rawType.getName()
              + "; at most one @JsonApiMeta property is allowed");
    }
  }

  private static void rejectDuplicateNames(
      List<? extends SemanticPropertyCarrier> properties,
      Class<?> rawType,
      String roleLabel,
      Function<String, MappingLocation> containerLocation) {
    Set<String> seen = new HashSet<>();
    for (SemanticPropertyCarrier property : properties) {
      if (!seen.add(property.jsonapiName())) {
        throw new JsonApiMappingException(
            MappingDiagnostic.NAME_COLLISION,
            rawType,
            containerLocation.apply(property.jsonapiName()),
            "Duplicate " + roleLabel + " name: " + property.jsonapiName());
      }
    }
  }

  private static void rejectAttributeRelationshipCollisions(
      List<? extends SemanticPropertyCarrier> attributeProperties,
      List<? extends SemanticPropertyCarrier> relationshipProperties,
      Class<?> rawType) {
    Set<String> relationshipNames = new HashSet<>();
    for (SemanticPropertyCarrier relationship : relationshipProperties) {
      relationshipNames.add(relationship.jsonapiName());
    }
    for (SemanticPropertyCarrier attribute : attributeProperties) {
      if (relationshipNames.contains(attribute.jsonapiName())) {
        // The colliding name could live under either container; no single member location applies.
        throw JsonApiMappingException.withoutLocation(
            MappingDiagnostic.NAME_COLLISION,
            rawType,
            "Attribute and relationship name collision: " + attribute.jsonapiName());
      }
    }
  }
}
