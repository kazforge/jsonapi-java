package com.kazforge.jsonapi.jackson2;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;

/**
 * Generic DTO shapes whose mapped member types must resolve from an explicitly bound parameterized
 * {@code JavaType}. Owned by the {@code JavaType}/generics entry-point tests in {@code
 * ResourceBinderSpec}, {@code PatchCommandBindingSpec}, and {@code PatchDtoBindingSpec}.
 */
public final class ParameterizedBindingFixtures {

  private ParameterizedBindingFixtures() {}

  /** Generic flat DTO whose relationship target must resolve from the bound parameterization. */
  @JsonApiResource(type = "articles")
  public record GenericArticle<T>(@JsonApiId String id, @JsonApiRelationship T author) {}

  /** Generic flat DTO whose attribute type must resolve from the bound parameterization. */
  @JsonApiResource(type = "things")
  public record GenericValue<T>(@JsonApiId String id, @JsonApiAttribute T value) {}

  /** Generic direct typed PATCH DTO; parameterization must survive introspection and binding. */
  @JsonApiResource(type = "articles")
  public record GenericPatch<T>(@JsonApiId T id, @JsonApiAttribute PatchPresence<T> title) {}
}
