package com.kazforge.jsonapi.fixtures.domainwrite;

import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;

/**
 * JavaBean inheritance on the write path: mapped properties declared on a non-resource base type
 * plus a subclass-owned member (ADR-004 ordinary bean semantics).
 */
public final class InheritedBlogFixtures {

  private InheritedBlogFixtures() {}

  public abstract static class BaseBlog {

    protected String id;
    protected String name;

    protected BaseBlog() {}

    protected BaseBlog(String id, String name) {
      this.id = id;
      this.name = name;
    }

    @JsonApiId
    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    @JsonApiAttribute
    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  @JsonApiResource(type = "blogs")
  public static final class ExtendedBlog extends BaseBlog {

    private String description;

    public ExtendedBlog() {}

    public ExtendedBlog(String id, String name, String description) {
      super(id, name);
      this.description = description;
    }

    @JsonApiAttribute
    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }
  }
}
