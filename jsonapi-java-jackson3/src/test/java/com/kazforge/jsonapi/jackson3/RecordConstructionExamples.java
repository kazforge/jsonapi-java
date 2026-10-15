package com.kazforge.jsonapi.jackson3;

import com.kazforge.jsonapi.api.ResourceWriteOptions;
import com.kazforge.jsonapi.core.model.DocumentData;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.core.model.JsonApiObject;
import com.kazforge.jsonapi.core.model.Link;
import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.Relationships;
import com.kazforge.jsonapi.core.model.ResourceObject;
import com.kazforge.jsonapi.document.DocumentEnvelope;
import java.util.Map;

/** Compile-checked replacement snippets for public-guide record construction examples. */
final class RecordConstructionExamples {

  private RecordConstructionExamples() {}

  /** Replacement construction in docs/site/relationships-and-documents.md. */
  static JsonApiDocument linksOnlyDocument() {
    Link authorUrl = new Link.StringLink("/articles/1/author");
    Links authorLinks = Links.ofLinks(Map.of("related", authorUrl));
    Relationship authorLink = Relationship.linkOnly(authorLinks);
    Relationships relationships = Relationships.ofRelationships(Map.of("author", authorLink));
    ResourceObject articleResource =
        ResourceObject.of("articles", "1").withRelationships(relationships);
    DocumentData articleData = new DocumentData.SingleResource(articleResource);
    return JsonApiDocument.withData(articleData);
  }

  /** Replacement construction in docs/site/configuration.md. */
  static ResourceWriteOptions explicitEmptyJsonApiOptions() {
    JsonApiObject explicit = JsonApiObject.empty();
    return ResourceWriteOptions.defaults().withEnvelope(DocumentEnvelope.ofJsonApi(explicit));
  }

  /** Replacement construction in docs/site/resources.md. */
  static ResourceWriteOptions metadataOptions() {
    Meta requestMeta = Meta.of(Map.of("request", "req-7"));
    DocumentEnvelope envelope = DocumentEnvelope.ofMeta(requestMeta);
    return ResourceWriteOptions.defaults().withEnvelope(envelope);
  }
}
