package io.github.kazemek.jsonapi.jackson2;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import io.github.kazemek.jsonapi.annotation.JsonApiAttribute;
import io.github.kazemek.jsonapi.annotation.JsonApiId;
import io.github.kazemek.jsonapi.annotation.JsonApiRelationship;
import io.github.kazemek.jsonapi.annotation.JsonApiResource;
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier;

/**
 * DTO shapes used to prove the ordinary flat-read directionality contract through the resource
 * binder. Owned by {@code ResourceBinderSpec}; each nested shape isolates one Jackson access
 * mechanic (setter-only, creator-only, injected creator, root-typed, write-only, getter-only,
 * view-restricted).
 */
@SuppressWarnings({"unused", "NullAway"})
public final class DirectionalityReadFixtures {

  private DirectionalityReadFixtures() {}

  @JsonApiResource(type = "setter-only")
  public static final class SetterOnly {

    @JsonApiId public String id;

    private String title;

    @JsonApiAttribute
    public void setTitle(String title) {
      this.title = title;
    }

    public String titleValue() {
      return title;
    }
  }

  @JsonApiResource(type = "creator-only")
  public static final class CreatorOnly {

    private final String id;
    private final String title;

    @JsonCreator
    public CreatorOnly(
        @JsonProperty("id") @JsonApiId String id,
        @JsonProperty("title") @JsonApiAttribute String title) {
      this.id = id;
      this.title = title;
    }

    public String idValue() {
      return id;
    }

    public String titleValue() {
      return title;
    }
  }

  @JsonApiResource(type = "injection-only")
  public static final class InjectionOnly {

    private final String id;
    private final String title;

    @JsonCreator
    public InjectionOnly(
        @JsonProperty("id") @JsonApiId String id,
        @JsonProperty("title")
            @JacksonInject(value = "injected-title", useInput = OptBoolean.FALSE)
            @JsonApiAttribute
            String title) {
      this.id = id;
      this.title = title;
    }

    public String idValue() {
      return id;
    }

    public String titleValue() {
      return title;
    }
  }

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.PROPERTY,
      property = "kind",
      defaultImpl = RootTyped.class)
  @JsonSubTypes({@JsonSubTypes.Type(value = RootTyped.class, name = "root-typed")})
  @JsonApiResource(type = "root-typed")
  public static final class RootTyped {

    @JsonApiId public String id;

    @JsonApiAttribute public String title;
  }

  @JsonApiResource(type = "write-only")
  public static final class WriteOnly {

    @JsonApiId public String id;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @JsonApiAttribute
    private String title;

    public String titleValue() {
      return title;
    }
  }

  /**
   * Direction-specific names intentionally cross between two logical properties. This proves that
   * read mapping pairs serialization annotations with the matching logical deserialization property
   * rather than the first property sharing an external name.
   */
  @JsonApiResource(type = "crossed-names")
  public static final class CrossedNames {

    @JsonApiId public String id;

    private String attribute;
    private ResourceIdentifier relationship;

    @JsonApiAttribute
    public String getAttribute() {
      return attribute;
    }

    public void setAttribute(String attribute) {
      this.attribute = attribute;
    }

    @JsonApiRelationship
    public ResourceIdentifier getRelationship() {
      return relationship;
    }

    public void setRelationship(ResourceIdentifier relationship) {
      this.relationship = relationship;
    }

    public String attributeValue() {
      return attribute;
    }

    public ResourceIdentifier relationshipValue() {
      return relationship;
    }
  }

  /**
   * Assigns crossed external names to read and write accessors while preserving each property's
   * Java logical identity.
   */
  public static final class CrossedDirectionPropertyNamingStrategy extends PropertyNamingStrategy {

    @Override
    public String nameForGetterMethod(
        MapperConfig<?> config, AnnotatedMethod method, String defaultName) {
      return switch (defaultName) {
        case "attribute" -> "relationship-wire";
        case "relationship" -> "attribute-wire";
        default -> defaultName;
      };
    }

    @Override
    public String nameForSetterMethod(
        MapperConfig<?> config, AnnotatedMethod method, String defaultName) {
      return switch (defaultName) {
        case "attribute" -> "attribute-wire";
        case "relationship" -> "relationship-wire";
        default -> defaultName;
      };
    }
  }

  @JsonApiResource(type = "getter-only")
  public static final class GetterOnly {

    @JsonApiId public String id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonApiAttribute
    public String getTitle() {
      return "derived";
    }
  }

  @JsonApiResource(type = "getter-only-id")
  public static final class GetterOnlyIdentifier {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonApiId
    public String getId() {
      return "derived";
    }
  }

  public static final class IncludedInReadView {}

  public static final class ExcludedFromReadView {}

  @JsonApiResource(type = "view-restricted")
  public static final class ViewRestricted {

    @JsonView(IncludedInReadView.class)
    @JsonApiId
    public String id;

    @JsonView(IncludedInReadView.class)
    @JsonApiAttribute
    public String visible;

    @JsonView(ExcludedFromReadView.class)
    @JsonApiAttribute
    @SuppressWarnings("unused")
    public String hidden;
  }
}
