package com.kazforge.jsonapi.mapping.contract;

import com.kazforge.jsonapi.core.model.Attributes;
import com.kazforge.jsonapi.core.model.DocumentData;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.Relationships;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.model.ResourceObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Representative black-box mapping contract shared by concrete JSON-library backends.
 *
 * <p>This deliberately tests observable JSON:API semantics rather than any mapper SPI shape.
 */
public final class MappingBackendContract {

  private MappingBackendContract() {}

  public static void verify(MappingContractAdapter adapter) {
    ResourceObject draft =
        adapter.toResource(new MappingContractFixtures.Draft("draft-1", "Draft"));
    requireEquals("drafts", draft.type(), "local-id resource type");
    requireEquals(null, draft.id(), "local-id resource id");
    requireEquals("draft-1", draft.lid(), "local-id resource lid");

    List<Object> nullableItems = new ArrayList<>();
    nullableItems.add("one");
    nullableItems.add(null);
    nullableItems.add("two");
    Map<String, Object> nullableObject = new LinkedHashMap<>();
    nullableObject.put("street", null);
    nullableObject.put("city", "Berlin");
    ResourceObject openValues =
        adapter.toResource(
            new MappingContractFixtures.OpenValues("open-1", nullableItems, nullableObject));
    Map<String, Object> expectedOpenAttributes = new LinkedHashMap<>();
    expectedOpenAttributes.put("items", nullableItems);
    expectedOpenAttributes.put("object", nullableObject);
    requireEquals(
        Attributes.ofAttributes(expectedOpenAttributes),
        openValues.attributes(),
        "nullable open values");

    MappingContractFixtures.Person author = new MappingContractFixtures.Person("p1", "Alice");
    MappingContractFixtures.Comment first = new MappingContractFixtures.Comment("c1", "First");
    MappingContractFixtures.Comment second = new MappingContractFixtures.Comment("c2", "Second");
    MappingContractFixtures.Article article =
        new MappingContractFixtures.Article("a1", "Hello", author, List.of(first, second));

    ResourceObject resource = adapter.toResource(article);
    requireEquals("articles", resource.type(), "resource type");
    requireEquals("a1", resource.id(), "resource id");
    requireEquals(
        Attributes.ofAttributes(Map.of("title", "Hello")),
        resource.attributes(),
        "resource attributes");

    Relationships relationships = Objects.requireNonNull(resource.relationships(), "relationships");
    requireEquals(
        Relationship.withData(
            new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "p1"))),
        relationships.relationships().get("author"),
        "to-one relationship");
    requireEquals(
        Relationship.withData(
            new RelationshipData.IdentifierCollectionLinkage(
                List.of(
                    ResourceIdentifier.of("comments", "c1"),
                    ResourceIdentifier.of("comments", "c2")))),
        relationships.relationships().get("comments"),
        "to-many relationship");

    JsonApiDocument document = adapter.toDocument(article, List.of("author", "comments"));
    if (!(document.data() instanceof DocumentData.SingleResource single)) {
      throw new AssertionError("expected single-resource primary data but got " + document.data());
    }
    requireEquals(resource, single.resource(), "document primary resource");

    List<ResourceObject> included = Objects.requireNonNull(document.included(), "included");
    requireEquals(3, included.size(), "included resource count");
    requireEquals(resource("people", "p1", "name", "Alice"), included.get(0), "included author");
    requireEquals(
        resource("comments", "c1", "body", "First"), included.get(1), "included comment 1");
    requireEquals(
        resource("comments", "c2", "body", "Second"), included.get(2), "included comment 2");

    MappingContractResult sparse =
        adapter.toMappedDocument(article, List.of("author"), Map.of("articles", List.of("title")));
    if (!(sparse.document().data() instanceof DocumentData.SingleResource sparseSingle)) {
      throw new AssertionError("expected sparse single-resource primary data");
    }
    ResourceObject sparsePrimary = sparseSingle.resource();
    requireEquals(
        Attributes.ofAttributes(Map.of("title", "Hello")),
        sparsePrimary.attributes(),
        "sparse attributes");
    requireEquals(null, sparsePrimary.relationships(), "sparse relationship omission");
    requireEquals(
        1,
        Objects.requireNonNull(sparse.document().included(), "sparse included").size(),
        "sparse included count");
    requireEquals(
        resource("people", "p1", "name", "Alice"),
        sparse.document().included().getFirst(),
        "sparse included author");
    requireEquals(
        java.util.Set.of(com.kazforge.jsonapi.core.model.ResourceIdentity.ofId("people", "p1")),
        sparse.sparseFieldsetLinkageExemptions(),
        "sparse linkage exemption");
  }

  private static ResourceObject resource(
      String type, String id, String attributeName, String attributeValue) {
    return new ResourceObject(
        type,
        id,
        null,
        Attributes.ofAttributes(Map.of(attributeName, attributeValue)),
        null,
        null,
        null,
        Map.of());
  }

  private static void requireEquals(Object expected, Object actual, String label) {
    if (!Objects.equals(expected, actual)) {
      throw new AssertionError(label + ": expected " + expected + " but got " + actual);
    }
  }
}
