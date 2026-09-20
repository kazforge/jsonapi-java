package com.kazforge.jsonapi.fixtures.compoundwrite;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import org.jspecify.annotations.Nullable;

/** Mutable node graph for cyclic inclusion traversal tests. */
@JsonApiResource(type = "nodes")
public final class CyclicNode {

  private final String id;
  private final String label;
  private @Nullable CyclicNode child;

  public CyclicNode(String id, String label) {
    this.id = id;
    this.label = label;
  }

  @JsonApiId
  public String getId() {
    return id;
  }

  @JsonApiAttribute
  public String getLabel() {
    return label;
  }

  @JsonApiRelationship
  public @Nullable CyclicNode getChild() {
    return child;
  }

  public void setChild(@Nullable CyclicNode child) {
    this.child = child;
  }
}
