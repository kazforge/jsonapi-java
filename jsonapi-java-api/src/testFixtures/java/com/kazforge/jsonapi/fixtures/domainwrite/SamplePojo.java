package com.kazforge.jsonapi.fixtures.domainwrite;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import java.util.List;

@JsonApiResource(type = "pojos")
public class SamplePojo {

  @JsonApiId private String id;

  @JsonApiAttribute
  @JsonProperty("display-name")
  private String name;

  @JsonApiRelationship private List<Comment> comments;

  public SamplePojo() {}

  public SamplePojo(String id, String name, List<Comment> comments) {
    this.id = id;
    this.name = name;
    this.comments = comments;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<Comment> getComments() {
    return comments;
  }

  public void setComments(List<Comment> comments) {
    this.comments = comments;
  }
}
