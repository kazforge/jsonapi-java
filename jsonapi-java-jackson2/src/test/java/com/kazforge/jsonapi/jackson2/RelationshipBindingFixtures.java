package com.kazforge.jsonapi.jackson2;

import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;

/**
 * DTO shape proving configured-Jackson missing-vs-explicit-null binding semantics for one
 * relationship property: a field initializer is retained when the relationship is unbound (wire
 * {@code data} absent) and overwritten when explicit {@code "data": null} binds. Owned by {@code
 * ResourceBinderSpec}.
 */
@SuppressWarnings({"unused", "NullAway"})
public final class RelationshipBindingFixtures {

  private RelationshipBindingFixtures() {}

  @JsonApiResource(type = "defaulted-relationship-articles")
  public static final class DefaultedRelationshipArticle {

    @JsonApiId public String id;

    @JsonApiRelationship private ResourceIdentifier author = defaultAuthor();

    public static ResourceIdentifier defaultAuthor() {
      return ResourceIdentifier.of("people", "default");
    }

    public ResourceIdentifier getAuthor() {
      return author;
    }

    public void setAuthor(ResourceIdentifier author) {
      this.author = author;
    }
  }
}
