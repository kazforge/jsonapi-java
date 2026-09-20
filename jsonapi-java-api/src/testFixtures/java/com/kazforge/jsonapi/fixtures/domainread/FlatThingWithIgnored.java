package com.kazforge.jsonapi.fixtures.domainread;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import java.util.Objects;

@JsonApiResource(type = "things")
public class FlatThingWithIgnored {

  @JsonApiId private String id;

  @JsonIgnore
  @JsonApiAttribute
  @JsonProperty("secret")
  private String confidential;

  private String name;

  public FlatThingWithIgnored() {}

  public FlatThingWithIgnored(String id, String name, String confidential) {
    this.id = id;
    this.name = name;
    this.confidential = confidential;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getConfidential() {
    return confidential;
  }

  public void setConfidential(String confidential) {
    this.confidential = confidential;
  }

  @JsonApiAttribute
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof FlatThingWithIgnored other)) {
      return false;
    }
    return Objects.equals(id, other.id)
        && Objects.equals(name, other.name)
        && Objects.equals(confidential, other.confidential);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, confidential);
  }
}
