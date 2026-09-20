package com.kazforge.jsonapi.mapping.contract;

import com.kazforge.jsonapi.core.model.Attributes;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.Relationships;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.core.model.ResourceObject;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Representative black-box contract for JSON:API core-to-application binding. */
public final class BindingBackendContract {

  private BindingBackendContract() {}

  public static void verify(BindingContractAdapter adapter) {
    ResourceObject resource =
        new ResourceObject(
            "articles",
            "a1",
            null,
            Attributes.ofAttributes(Map.of("title", "Hello")),
            Relationships.ofRelationships(
                Map.of(
                    "author",
                    Relationship.withData(
                        new RelationshipData.SingleLinkage(ResourceIdentifier.of("people", "p1"))),
                    "comments",
                    Relationship.withData(
                        new RelationshipData.IdentifierCollectionLinkage(
                            List.of(
                                ResourceIdentifier.of("comments", "c1"),
                                ResourceIdentifier.of("comments", "c2")))))),
            null,
            null,
            Map.of());

    MappingContractFixtures.BoundArticle article =
        adapter.fromResource(resource, MappingContractFixtures.BoundArticle.class);

    requireEquals("a1", article.id, "bound id");
    requireEquals("Hello", article.title, "bound attribute");
    requireEquals(ResourceIdentifier.of("people", "p1"), article.author, "bound to-one linkage");
    requireEquals(
        List.of(ResourceIdentifier.of("comments", "c1"), ResourceIdentifier.of("comments", "c2")),
        article.comments,
        "bound to-many linkage");

    ResourceObject draft =
        new ResourceObject(
            "drafts",
            null,
            "draft-1",
            Attributes.ofAttributes(Map.of("title", "Draft")),
            null,
            null,
            null,
            Map.of());
    MappingContractFixtures.BoundDraft boundDraft =
        adapter.fromResource(draft, MappingContractFixtures.BoundDraft.class);
    requireEquals("draft-1", boundDraft.localId, "bound local id");
    requireEquals("Draft", boundDraft.title, "bound draft attribute");

    ResourceObject explicitNull =
        new ResourceObject(
            "articles",
            "a2",
            null,
            null,
            Relationships.ofRelationships(
                Map.of("author", Relationship.withData(RelationshipData.NullLinkage.INSTANCE))),
            null,
            null,
            Map.of());
    MappingContractFixtures.BoundArticle nullArticle =
        adapter.fromResource(explicitNull, MappingContractFixtures.BoundArticle.class);
    requireEquals(null, nullArticle.author, "explicit-null to-one linkage");
  }

  private static void requireEquals(Object expected, Object actual, String label) {
    if (!Objects.equals(expected, actual)) {
      throw new AssertionError(label + ": expected " + expected + " but got " + actual);
    }
  }
}
