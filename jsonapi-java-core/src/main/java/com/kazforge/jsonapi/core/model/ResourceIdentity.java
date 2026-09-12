package com.kazforge.jsonapi.core.model;

import com.kazforge.jsonapi.core.validation.LocalValidation;

/** Structured resource identity key: kind + type + value (avoids delimiter collisions). */
public record ResourceIdentity(Kind kind, String type, String value) {

  public enum Kind {
    ID,
    LID
  }

  public ResourceIdentity {
    LocalValidation.requireNonNull(kind, "/resourceIdentity/kind", "kind must not be null");
    LocalValidation.requireNonNull(type, "/resourceIdentity/type", "type must not be null");
    LocalValidation.requireNonNull(value, "/resourceIdentity/value", "value must not be null");
  }

  public static ResourceIdentity ofId(String type, String id) {
    return new ResourceIdentity(Kind.ID, type, id);
  }

  public static ResourceIdentity ofLid(String type, String lid) {
    return new ResourceIdentity(Kind.LID, type, lid);
  }

  public boolean isId() {
    return kind == Kind.ID;
  }

  public boolean isLid() {
    return kind == Kind.LID;
  }
}
