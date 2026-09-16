package com.kazforge.jsonapi.core.model;

import com.kazforge.jsonapi.core.validation.LocalValidation;

/**
 * Structured resource identity key that keeps {@code id} and {@code lid} identities distinct.
 *
 * <p>Type and value are retained as separate exact strings rather than concatenated, so embedded
 * delimiters cannot create collisions. This key does not make {@code id} and {@code lid}
 * interchangeable; aggregate validation binds aliases only when a document supplies both.
 */
public record ResourceIdentity(Kind kind, String type, String value) {

  /** The JSON:API identity member represented by {@link #value()}. */
  public enum Kind {
    ID,
    LID
  }

  public ResourceIdentity {
    LocalValidation.requireNonNull(kind, "/resourceIdentity/kind", "kind must not be null");
    LocalValidation.requireNonNull(type, "/resourceIdentity/type", "type must not be null");
    LocalValidation.requireNonNull(value, "/resourceIdentity/value", "value must not be null");
  }

  /** Creates an identity keyed by the resource {@code id}. */
  public static ResourceIdentity ofId(String type, String id) {
    return new ResourceIdentity(Kind.ID, type, id);
  }

  /** Creates an identity keyed by the resource {@code lid}. */
  public static ResourceIdentity ofLid(String type, String lid) {
    return new ResourceIdentity(Kind.LID, type, lid);
  }

  /** Whether this key uses the resource {@code id}. */
  public boolean isId() {
    return kind == Kind.ID;
  }

  /** Whether this key uses the resource {@code lid}. */
  public boolean isLid() {
    return kind == Kind.LID;
  }
}
