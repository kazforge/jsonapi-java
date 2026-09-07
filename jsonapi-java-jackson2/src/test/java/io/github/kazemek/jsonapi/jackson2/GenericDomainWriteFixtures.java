package io.github.kazemek.jsonapi.jackson2;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.github.kazemek.jsonapi.annotation.JsonApiAttribute;
import io.github.kazemek.jsonapi.annotation.JsonApiId;
import io.github.kazemek.jsonapi.annotation.JsonApiRelationship;
import io.github.kazemek.jsonapi.annotation.JsonApiResource;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Models used to prove declared generic types survive the Jackson 2 domain write pipeline. Owned by
 * {@code GenericDomainWriteSpec}; these shapes exercise {@code JavaType}-based generic writes, not
 * shared wire semantics. Adapter-local Jackson 2 copy of the shared shapes.
 */
@SuppressWarnings({"ClassCanBeRecord", "OptionalUsedAsFieldOrParameterType", "rawtypes", "unused"})
public final class GenericDomainWriteFixtures {

  private GenericDomainWriteFixtures() {}

  /** A resource whose mapped members are all parameterized by its root type variable. */
  @JsonApiResource(type = "generic-resources")
  public static class GenericResource<T> {

    @JsonApiId private final String id;

    @JsonApiAttribute
    @JsonSerialize(typing = JsonSerialize.Typing.STATIC)
    private final T value;

    @JsonApiRelationship private final T related;
    @JsonApiRelationship private final List<T> many;
    @JsonApiRelationship private final Optional<T> optional;

    public GenericResource(String id, T value, T related, List<T> many, Optional<T> optional) {
      this.id = id;
      this.value = value;
      this.related = related;
      this.many = many;
      this.optional = optional;
    }

    public String getId() {
      return id;
    }

    public T getValue() {
      return value;
    }

    public T getRelated() {
      return related;
    }

    public List<T> getMany() {
      return many;
    }

    public Optional<T> getOptional() {
      return optional;
    }
  }

  /** A relationship-only generic resource used for member-location diagnostics. */
  @JsonApiResource(type = "generic-relations")
  public static class GenericRelationship<T> {

    @JsonApiId private final String id;
    @JsonApiRelationship private final T related;

    public GenericRelationship(String id, T related) {
      this.id = id;
      this.related = related;
    }

    public String getId() {
      return id;
    }

    public T getRelated() {
      return related;
    }
  }

  /** A concrete subtype with a directly resolvable generic superclass binding. */
  @JsonApiResource(type = "generic-resources")
  public static final class ThingResource extends GenericResource<GenericThing> {

    public ThingResource(
        String id,
        GenericThing value,
        GenericThing related,
        List<GenericThing> many,
        Optional<GenericThing> optional) {
      super(id, value, related, many, optional);
    }
  }

  /** A generic declaration whose only type variable is intentionally unmapped. */
  @JsonApiResource(type = "irrelevant-generics")
  public static final class IrrelevantGeneric<T> {

    @JsonApiId private final String id;
    @JsonIgnore private final T ignored;

    public IrrelevantGeneric(String id, T ignored) {
      this.id = id;
      this.ignored = ignored;
    }

    public String getId() {
      return id;
    }

    public T getIgnored() {
      return ignored;
    }
  }

  /** A raw container member is valid when it does not depend on the root type variable. */
  @JsonApiResource(type = "raw-map-generics")
  public static final class RawMapGeneric<T> {

    @JsonApiId private final String id;
    @JsonApiAttribute private final Map<?, ?> values;
    @JsonIgnore private final T ignored;

    public RawMapGeneric(String id, Map<?, ?> values, T ignored) {
      this.id = id;
      this.values = values;
      this.ignored = ignored;
    }

    public String getId() {
      return id;
    }

    public Map<?, ?> getValues() {
      return values;
    }

    public T getIgnored() {
      return ignored;
    }
  }

  @JsonApiResource(type = "raw-relationships")
  public static final class RawRelationship {

    @JsonApiId private final String id;
    @JsonApiRelationship private final Optional relation;

    public RawRelationship(String id, Optional relation) {
      this.id = id;
      this.relation = relation;
    }

    public String getId() {
      return id;
    }

    public Optional getRelation() {
      return relation;
    }
  }

  @JsonApiResource(type = "wildcard-relationships")
  public static final class WildcardRelationship {

    @JsonApiId private final String id;
    @JsonApiRelationship private final Optional<?> relation;

    public WildcardRelationship(String id, Optional<?> relation) {
      this.id = id;
      this.relation = relation;
    }

    public String getId() {
      return id;
    }

    public Optional<?> getRelation() {
      return relation;
    }
  }

  @JsonApiResource(type = "raw-collection-relationships")
  public static final class RawCollectionRelationship {

    @JsonApiId private final String id;
    @JsonApiRelationship private final List relation;

    public RawCollectionRelationship(String id, List relation) {
      this.id = id;
      this.relation = relation;
    }

    public String getId() {
      return id;
    }

    public List getRelation() {
      return relation;
    }
  }

  @JsonApiResource(type = "things")
  public static final class GenericThing {

    @JsonApiId private final String id;
    private final String name;

    public GenericThing(String id, String name) {
      this.id = id;
      this.name = name;
    }

    public String getId() {
      return id;
    }

    @JsonApiAttribute
    public String getName() {
      return name;
    }
  }

  @JsonApiResource(type = "other-things")
  public static final class OtherThing {

    @JsonApiId private final String id;
    private final String name;

    public OtherThing(String id, String name) {
      this.id = id;
      this.name = name;
    }

    public String getId() {
      return id;
    }

    @JsonApiAttribute
    public String getName() {
      return name;
    }
  }

  /** Interface bound used to make static property serialization distinguish declared type data. */
  public interface ScalarView {

    String getBase();
  }

  public static final class ScalarValue implements ScalarView {

    private final String base;
    private final String extra;

    public ScalarValue(String base, String extra) {
      this.base = base;
      this.extra = extra;
    }

    @Override
    public String getBase() {
      return base;
    }

    public String getExtra() {
      return extra;
    }
  }
}
