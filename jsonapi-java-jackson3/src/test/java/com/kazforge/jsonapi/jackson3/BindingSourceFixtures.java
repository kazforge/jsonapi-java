package com.kazforge.jsonapi.jackson3;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonMerge;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.Nulls;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiLocalId;
import com.kazforge.jsonapi.annotation.JsonApiMeta;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiRelationshipMeta;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.mapping.RelationshipLinkage;
import com.kazforge.jsonapi.patch.PatchPresence;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Application shapes for exact successful-binding attribution. */
@SuppressWarnings({"unused", "NullAway", "ArrayRecordComponent"})
public final class BindingSourceFixtures {

  private BindingSourceFixtures() {}

  public record Details(
      String postalCode,
      String value,
      String missing,
      @JsonProperty("a/b~c") String escaped,
      @JsonIgnore String ignored) {}

  public abstract static class DetailsMixin {
    @JsonProperty("zip_code")
    public abstract String postalCode();
  }

  public record NestedPatch(
      PatchPresence<String> postalCode,
      PatchPresence<String> value,
      PatchPresence<String> missing,
      @JsonProperty("a/b~c") PatchPresence<String> escaped) {}

  @JsonApiResource(type = "articles")
  public record GenericSourcePatch<T>(
      @JsonApiId String id, @JsonApiAttribute PatchPresence<T> title) {}

  @JsonApiResource(type = "source-things")
  public record Create(
      @JsonApiId String id,
      @JsonApiLocalId String localId,
      @JsonApiAttribute @JsonProperty("display-name") String displayName,
      @JsonApiAttribute Details details,
      @JsonApiAttribute List<Details> items,
      @JsonApiAttribute Details[] array,
      @JsonApiAttribute Optional<Details> optional,
      @JsonApiAttribute List<String> labels,
      @JsonApiRelationship ResourceIdentifier author,
      @JsonApiRelationship List<ResourceIdentifier> comments) {}

  @JsonApiResource(type = "source-things")
  public record StringListPatch(
      @JsonApiId String id, @JsonApiAttribute PatchPresence<List<String>> labels) {}

  @JsonApiResource(type = "source-things")
  public record Patch(
      @JsonApiId String id,
      @JsonApiAttribute @JsonProperty("display-name") PatchPresence<String> displayName,
      @JsonApiAttribute PatchPresence<Details> details,
      @JsonApiAttribute PatchPresence<NestedPatch> nested,
      @JsonApiAttribute PatchPresence<List<Details>> items,
      @JsonApiAttribute PatchPresence<Details[]> array,
      @JsonApiAttribute PatchPresence<Optional<Details>> optional,
      @JsonApiRelationship PatchPresence<ResourceIdentifier> author,
      @JsonApiRelationship PatchPresence<List<ResourceIdentifier>> comments) {}

  public record Aliased(@JsonAlias("alternate") String name) {}

  @JsonApiResource(type = "merged-source")
  public static final class Merged {
    @JsonApiId public String id;

    @JsonApiAttribute @JsonMerge
    public List<Details> items =
        new ArrayList<>(List.of(new Details(null, "server", null, null, null)));
  }

  public record Unwrapped(@JsonUnwrapped Details inner) {}

  @JsonApiResource(type = "contextual-source")
  public record Contextual(
      @JsonApiId String id,
      @JsonApiAttribute @JsonIgnoreProperties("value") Details filtered,
      @JsonApiAttribute Unwrapped flattened) {}

  @JsonApiResource(type = "ignored-source")
  @JsonIgnoreProperties("value")
  public record IgnoredRoot(@JsonApiId String id, @JsonApiAttribute String value) {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
  @JsonSubTypes(@JsonSubTypes.Type(value = RenamedValue.class, name = "renamed"))
  public static class PolymorphicValue {
    public String value;
  }

  public static final class RenamedValue extends PolymorphicValue {
    @JsonProperty("newName")
    public void setValue(String value) {
      this.value = value;
    }
  }

  @JsonApiResource(type = "case-source")
  public record CaseNames(
      @JsonApiId String id,
      @JsonApiAttribute String name,
      @JsonApiAttribute @JsonProperty("NAME") String upper) {}

  @JsonApiResource(type = "boundaries")
  public record Boundaries(
      @JsonApiId String id,
      @JsonApiAttribute Set<Details> unordered,
      @JsonApiAttribute @JsonSetter(contentNulls = Nulls.SKIP) List<Details> skipped,
      @JsonApiAttribute Aliased aliased,
      @JsonApiAttribute Details parent,
      @JsonApiAttribute String defaulted) {
    public Boundaries {
      if (defaulted == null) {
        defaulted = "default";
      }
    }
  }

  @JsonApiResource(type = "source-links")
  public record Links(
      @JsonApiId String id,
      @JsonApiRelationship ResourceIdentifier author,
      @JsonApiRelationship List<RelationshipLinkage<ResourceIdentifier, Details>> owners,
      @JsonApiRelationship Set<ResourceIdentifier> tags,
      @JsonApiMeta Details metadata,
      @JsonApiRelationshipMeta(relationship = "author") Details authorMetadata) {}

  @JsonApiResource(type = "source-links")
  public record LinkPatch(
      @JsonApiId String id,
      @JsonApiRelationship PatchPresence<ResourceIdentifier> author,
      @JsonApiRelationship
          PatchPresence<List<RelationshipLinkage<ResourceIdentifier, Details>>> owners,
      @JsonApiRelationship PatchPresence<Set<ResourceIdentifier>> tags,
      @JsonApiMeta PatchPresence<Details> metadata,
      @JsonApiRelationshipMeta(relationship = "author") PatchPresence<Details> authorMetadata) {}

  @JsonApiResource(type = "mutable-source")
  public static final class Mutable {
    @JsonApiId public String id;
    @JsonApiAttribute public Details details;
  }
}
