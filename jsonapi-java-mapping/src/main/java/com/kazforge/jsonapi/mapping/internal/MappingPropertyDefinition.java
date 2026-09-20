package com.kazforge.jsonapi.mapping.internal;

import java.util.Objects;

/**
 * Backend-neutral description of one mapped property.
 *
 * <p>The backend type token and property handle remain opaque to the shared mapping domain.
 */
public record MappingPropertyDefinition<T, P>(
    P handle,
    String logicalName,
    String externalName,
    String jsonapiName,
    MappingRole role,
    T declaredType,
    boolean toMany) {

  public MappingPropertyDefinition {
    Objects.requireNonNull(handle, "handle");
    Objects.requireNonNull(logicalName, "logicalName");
    Objects.requireNonNull(externalName, "externalName");
    Objects.requireNonNull(jsonapiName, "jsonapiName");
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(declaredType, "declaredType");
    if (role == MappingRole.ID && !"id".equals(jsonapiName)) {
      throw new IllegalArgumentException("JSON:API id property must use wire member 'id'");
    }
    if (role == MappingRole.LOCAL_ID && !"lid".equals(jsonapiName)) {
      throw new IllegalArgumentException("JSON:API local-id property must use wire member 'lid'");
    }
  }
}
