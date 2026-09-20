package com.kazforge.jsonapi.mapping.internal;

import java.util.Objects;

/** Backend-neutral deserialization view of one JSON:API-mapped application property. */
public record BindingPropertyDefinition<T, P>(
    P handle,
    String logicalName,
    String externalName,
    String jsonapiName,
    MappingRole role,
    T declaredType,
    boolean bindable) {

  public BindingPropertyDefinition {
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
