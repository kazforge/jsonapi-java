package com.kazforge.jsonapi.fixtures.domainwrite;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.fixtures.domainpatch.AuthorIdMeta;
import com.kazforge.jsonapi.jackson.mapping.RelationshipLinkage;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Passive declaration-shape carriers for adapter-local write-diagnostics specifications:
 * deliberately mis-declared resources (missing annotations, duplicate roles, name collisions,
 * invalid or reserved names), throwing accessors, write-only properties, unsupported relationship
 * value shapes, and invalid {@code RelationshipLinkage} declarations (ADR-017). Adapter suites map
 * instances of these carriers through their own resource writer and assert the semantic diagnostic
 * categories and wire locations.
 */
public final class WriteDiagnosticsFixtures {

  private WriteDiagnosticsFixtures() {}

  /** Resource declaration with an empty {@code type} value. */
  @JsonApiResource(type = "")
  public record EmptyTypeEntity(@JsonApiId String id) {}

  /** Resource declaration whose {@code type} contains characters JSON:API forbids. */
  @JsonApiResource(type = "bad type!")
  public record InvalidTypeEntity(@JsonApiId String id) {}

  /** Annotated resource without any identifier property. */
  @JsonApiResource(type = "entities")
  public record NoIdEntity(String name) {}

  /** Annotated resource whose identifier property holds null. */
  @JsonApiResource(type = "entities")
  public record NullIdEntity(@JsonApiId @Nullable String id) {}

  /** Identifier member carrying a second role annotation. */
  @JsonApiResource(type = "dup")
  public record DuplicateRoleEntity(@JsonApiId @JsonApiAttribute String id) {}

  /** Attribute and relationship mapped onto the same Jackson external name. */
  @JsonApiResource(type = "collision")
  public record NameCollisionEntity(
      @JsonApiId String id,
      @JsonApiAttribute @JsonProperty("same") String fieldA,
      @JsonApiRelationship @JsonProperty("same") String fieldB) {}

  /**
   * Field-only POJO whose attribute and relationship share one Jackson external name. Jackson
   * merges the members and cannot expose either field; mapping must still report {@code
   * NAME_COLLISION} rather than skipping both roles.
   */
  @SuppressWarnings("java:S1104")
  @JsonApiResource(type = "field-collision")
  public static final class FieldOnlyNameCollisionEntity {
    @JsonApiId public final String id;

    @JsonApiAttribute
    @JsonProperty("same")
    public final String fieldA;

    @JsonApiRelationship
    @JsonProperty("same")
    public final String fieldB;

    public FieldOnlyNameCollisionEntity(String id, String fieldA, String fieldB) {
      this.id = id;
      this.fieldA = fieldA;
      this.fieldB = fieldB;
    }
  }

  /** Two attributes mapped onto the same wire name. */
  @JsonApiResource(type = "dup-attrs")
  public record DuplicateAttrNameEntity(
      @JsonApiId String id,
      @JsonApiAttribute @JsonProperty("same") String fieldA,
      @JsonApiAttribute @JsonProperty("same") String fieldB) {}

  /** Two relationships mapped onto the same wire name. */
  @JsonApiResource(type = "dup-rels")
  public record DuplicateRelNameEntity(
      @JsonApiId String id,
      @JsonApiRelationship @JsonProperty("same") String otherA,
      @JsonApiRelationship @JsonProperty("same") String otherB) {}

  /** Attribute Jackson name containing characters JSON:API forbids. */
  @JsonApiResource(type = "invalid")
  public record InvalidAttrNameEntity(
      @JsonApiId String id, @JsonApiAttribute @JsonProperty("bad name!") String value) {}

  /** Attribute Jackson name using the reserved {@code type} member name. */
  @JsonApiResource(type = "reserved-attr")
  public record ReservedAttrNameEntity(
      @JsonApiId String id, @JsonApiAttribute @JsonProperty("type") String value) {}

  /** Relationship Jackson name containing characters JSON:API forbids. */
  @JsonApiResource(type = "invalid-rel")
  public record InvalidRelNameEntity(
      @JsonApiId String id, @JsonApiRelationship @JsonProperty("bad name!") String other) {}

  /** Relationship Jackson name using the reserved {@code id} member name. */
  @JsonApiResource(type = "reserved-rel")
  public record ReservedRelNameEntity(
      @JsonApiId String id, @JsonApiRelationship @JsonProperty("id") String other) {}

  /**
   * Attribute getter that always throws. Kept as a JavaBean so adapter-local specs cover a
   * conventional getter failure; records cannot declare checked-exception accessors.
   */
  @SuppressWarnings({"java:S6206", "java:S1068"})
  @JsonApiResource(type = "failing-attr")
  public static final class FailingAttrEntity {
    @JsonApiId private final String id;
    @JsonApiAttribute private final String badAttr;

    public FailingAttrEntity(String id, String badAttr) {
      this.id = id;
      this.badAttr = badAttr;
    }

    public String getId() {
      return id;
    }

    public String getBadAttr() throws java.io.IOException {
      throw new java.io.IOException("attribute read failure");
    }
  }

  /**
   * Renamed attribute getter that always throws; failures must report the wire name. Kept as a
   * JavaBean so adapter-local specs cover a conventional getter failure; records cannot declare
   * checked-exception accessors.
   */
  @SuppressWarnings({"java:S6206", "java:S1068"})
  @JsonApiResource(type = "renamed-failing-attr")
  public static final class RenamedFailingAttrEntity {
    @JsonApiId private final String id;

    @JsonApiAttribute
    @JsonProperty("body-text")
    private final String badAttr;

    public RenamedFailingAttrEntity(String id, String badAttr) {
      this.id = id;
      this.badAttr = badAttr;
    }

    public String getId() {
      return id;
    }

    public String getBadAttr() throws java.io.IOException {
      throw new java.io.IOException("attribute read failure");
    }
  }

  /**
   * Identifier getter that always throws. Kept as a JavaBean so adapter-local specs cover a
   * conventional getter failure; records cannot declare checked-exception accessors.
   */
  @SuppressWarnings({"java:S6206", "java:S1068"})
  @JsonApiResource(type = "failing-id")
  public static final class FailingIdEntity {
    @JsonApiId private final String id;

    public FailingIdEntity(String id) {
      this.id = id;
    }

    public String getId() throws java.io.IOException {
      throw new java.io.IOException("id read failure");
    }
  }

  /** Annotated property with only a setter and no readable accessor. */
  @JsonApiResource(type = "write-only")
  public static final class MissingAccessorEntity {
    @JsonApiId private final String id;
    private @Nullable String secret;

    public MissingAccessorEntity(String id) {
      this.id = id;
    }

    public String getId() {
      return id;
    }

    @JsonApiAttribute
    public void setSecret(@Nullable String secret) {
      this.secret = secret;
    }
  }

  /** To-many relationship declared as an unsupported runtime array type. */
  @JsonApiResource(type = "raw-array-rel")
  public record RenamedArrayRelEntity(
      @JsonApiId String id, @JsonApiRelationship @JsonProperty("ext-values") long[] values) {
    @Override
    public boolean equals(Object obj) {
      return obj instanceof RenamedArrayRelEntity(String otherId, long[] otherValues)
          && Objects.equals(id, otherId)
          && Arrays.equals(values, otherValues);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id, Arrays.hashCode(values));
    }

    @Override
    public String toString() {
      return "RenamedArrayRelEntity[id=" + id + ", values=" + Arrays.toString(values) + "]";
    }
  }

  /** To-many relationship holding mixed element types. */
  @JsonApiResource(type = "mixed-rel")
  public record RenamedMixedRelEntity(
      @JsonApiId String id, @JsonApiRelationship @JsonProperty("ext-items") List<Object> items) {}

  /** Raw erased iterable: to-many by declaration with no resolvable content type. */
  public static final class RawBag implements Iterable<Object> {
    private final List<Object> items;

    public RawBag(Object... items) {
      this.items = Arrays.asList(items);
    }

    @Override
    public Iterator<Object> iterator() {
      return items.iterator();
    }
  }

  /** To-many relationship declared with an unresolvable erased iterable type. */
  @JsonApiResource(type = "bag-rel")
  public record RenamedBagRelEntity(
      @JsonApiId String id, @JsonApiRelationship @JsonProperty("ext-bag") RawBag things) {}

  /** Raw erased {@code RelationshipLinkage} (generic information is required). */
  @JsonApiResource(type = "articles")
  @SuppressWarnings("rawtypes")
  public record RawRelationshipLinkageEntity(
      @JsonApiId String id, @JsonApiRelationship RelationshipLinkage author) {}

  /** Nested {@code RelationshipLinkage} as the wrapped target type. */
  @JsonApiResource(type = "articles")
  public record NestedRelationshipLinkageEntity(
      @JsonApiId String id,
      @JsonApiRelationship
          RelationshipLinkage<RelationshipLinkage<ResourceIdentifier, AuthorIdMeta>, AuthorIdMeta>
              author) {}

  /** Scalar identifier-meta type inside the wrapper (must be Bean / Map / Object). */
  @JsonApiResource(type = "articles")
  public record ScalarMetaRelationshipLinkageEntity(
      @JsonApiId String id,
      @JsonApiRelationship RelationshipLinkage<ResourceIdentifier, String> author) {}

  /** List-shaped identifier-meta type inside the wrapper (must be a whole-meta object). */
  @JsonApiResource(type = "articles")
  public record ListMetaRelationshipLinkageEntity(
      @JsonApiId String id,
      @JsonApiRelationship RelationshipLinkage<ResourceIdentifier, List<AuthorIdMeta>> author) {}

  /** Empty {@link Optional} identifier is {@code MISSING_IDENTIFIER}. */
  @JsonApiResource(type = "articles")
  public record EmptyOptionalIdEntity(
      @JsonApiId Optional<String> id, @JsonApiAttribute String title) {}

  /** Declared {@code List<Object>} to-many is an unresolvable collection element type. */
  @JsonApiResource(type = "articles")
  public record ObjectElementListRelEntity(
      @JsonApiId String id, @JsonApiRelationship List<Object> items) {}
}
