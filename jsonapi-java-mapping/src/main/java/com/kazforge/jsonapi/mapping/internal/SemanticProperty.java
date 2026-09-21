package com.kazforge.jsonapi.mapping.internal;

import com.kazforge.jsonapi.core.model.JsonApiMembers;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/**
 * Backend-neutral identity and naming of one adapter-local mapping property.
 *
 * <p>The three names stay distinct. {@code logicalName} is the backend's internal property identity
 * (for Jackson, the Java field, record component, or JavaBean name) and is the identity
 * relationship-meta declarations target. {@code externalName} is the configured backend external
 * name used for construction and conversion. {@code jsonapiName} is the JSON:API member name on the
 * wire.
 *
 * <p>Role and name invariants are adapter-independent and enforced on construction: the identifier
 * role always names the {@code id} member, the local-identifier role always names the {@code lid}
 * member, resource meta always names the {@code meta} member, and attributes and relationships use
 * their backend external name as the JSON:API member name. {@link PropertyRole#RELATIONSHIP_META}
 * is valid only in its resolved form, where {@code jsonapiName} is the matched target
 * relationship's JSON:API name while the meta property's own external name remains its construction
 * key.
 *
 * <p>This type is unsupported implementation detail for backend cooperation, not consumer SPI, and
 * must not appear in supported backend public signatures.
 */
@NullMarked
public record SemanticProperty(
    PropertyRole role, String logicalName, String externalName, String jsonapiName) {

  public SemanticProperty {
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(logicalName, "logicalName");
    Objects.requireNonNull(externalName, "externalName");
    Objects.requireNonNull(jsonapiName, "jsonapiName");
    requireRoleNameInvariant(role, externalName, jsonapiName);
  }

  private static void requireRoleNameInvariant(
      PropertyRole role, String externalName, String jsonapiName) {
    switch (role) {
      case ID -> requireFixedJsonapiName(role, jsonapiName, JsonApiMembers.ID);
      case LOCAL_ID -> requireFixedJsonapiName(role, jsonapiName, JsonApiMembers.LID);
      case RESOURCE_META -> requireFixedJsonapiName(role, jsonapiName, JsonApiMembers.META);
      case ATTRIBUTE, RELATIONSHIP -> requireExternalName(role, externalName, jsonapiName);
      case RELATIONSHIP_META -> requireResolvedRelationshipMeta(jsonapiName);
    }
  }

  private static void requireFixedJsonapiName(
      PropertyRole role, String jsonapiName, String expectedName) {
    if (!expectedName.equals(jsonapiName)) {
      throw new IllegalArgumentException(
          role + " JSON:API name must be '" + expectedName + "', was '" + jsonapiName + "'");
    }
  }

  private static void requireExternalName(
      PropertyRole role, String externalName, String jsonapiName) {
    if (!jsonapiName.equals(externalName)) {
      throw new IllegalArgumentException(
          role
              + " JSON:API name must equal the backend external name '"
              + externalName
              + "', was '"
              + jsonapiName
              + "'");
    }
  }

  private static void requireResolvedRelationshipMeta(String jsonapiName) {
    if (jsonapiName.isEmpty()) {
      throw new IllegalArgumentException(
          "RELATIONSHIP_META is valid only in resolved form and requires a non-empty target JSON:API"
              + " name");
    }
  }
}
