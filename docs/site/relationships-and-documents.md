---
title: "JSON:API relationships, linkage, and documents"
description: "Read and write JSON:API relationship linkage and error documents in Java. Preserve absent, null, and empty document states without loading related resources."
---

# JSON:API relationships, linkage, and documents {#relationships-and-documents}

In [Resources](resources.md#write-relationships-read-linkage), an article carried its author
linkage inside `data.relationships.author.data` as part of a resource document (`GET /articles/1`).
JSON:API also defines standalone **relationship linkage documents** (such as
`GET /articles/1/relationships/author`), complete **document representations** for states that a
flat DTO cannot express, and **error documents**.

Reuse the configured `JsonApi api` from [runtime setup](getting-started.md#configure-a-runtime).
JSON input names such as `responseBody` refer to Java strings containing the displayed document,
and output JSON is formatted for readability.

## Relationship linkage documents

In a resource document (`GET /articles/1`), primary `data` is the article resource and relationship
linkage is nested under `data.relationships`. On an independent relationship endpoint (such as
`GET /articles/1/relationships/author`), primary `data` is the **linkage itself**—a resource
identifier (`type` and `id`), `null`, or an identifier array.

`api.relationships()` reads and writes these linkage documents without a DTO class or
`ResourceTypeRegistry`. It handles identifiers only, not related-resource lookup or persistence.
To update relationship linkage inside a resource `PATCH /articles/1` payload instead of a
dedicated relationship endpoint, see
[Relationships replace linkage](patches.md#relationships-replace-linkage).

Write a to-one linkage document for an existing author (`people` / `p1`):

```java
import com.kazforge.jsonapi.core.model.ResourceIdentifier;

ResourceIdentifier author = ResourceIdentifier.of("people", "p1");
String authorJson = api.relationships().writeToOne(author);
```

`authorJson` contains this linkage document:

```json
{
  "data": {
    "type": "people",
    "id": "p1"
  }
}
```

Read it back as a `ResourceIdentifier`, without registering a `Person` DTO:

```java
ResourceIdentifier linkedAuthor = api.relationships().readToOne(authorJson);
```

`linkedAuthor.type()` is `"people"` and `linkedAuthor.id()` is `"p1"`.

### Null to-one linkage

When an article has no author—or a client clears a to-one relationship endpoint—primary `data` is
explicit JSON `null`:

```java
String noAuthorJson = api.relationships().writeToOne(null);
```

`noAuthorJson` contains:

```json
{
  "data": null
}
```

Reading `noAuthorJson` with `readToOne` returns Java `null`:

```java
ResourceIdentifier clearedAuthor = api.relationships().readToOne(noAuthorJson);
```

`clearedAuthor` is `null`.

### To-many linkage

For a to-many relationship endpoint (such as an article's co-authors or reviewers), write the
identifiers as a collection:

```java
import java.util.List;

List<ResourceIdentifier> authors = List.of(
    author,
    ResourceIdentifier.of("people", "p2")
);
String authorsJson = api.relationships().writeToMany(authors);
```

`authorsJson` contains:

```json
{
  "data": [
    {
      "type": "people",
      "id": "p1"
    },
    {
      "type": "people",
      "id": "p2"
    }
  ]
}
```

Read a to-many linkage document with `readToMany`:

```java
List<ResourceIdentifier> linkedAuthors = api.relationships().readToMany(authorsJson);
```

`linkedAuthors` contains the `people` / `p1` and `people` / `p2` identifiers in order.

An empty to-many relationship uses an empty array `[]`, which is a distinct wire state from `null`:

```java
String emptyAuthorsJson = api.relationships().writeToMany(List.of());
```

`emptyAuthorsJson` contains:

```json
{
  "data": []
}
```

```java
List<ResourceIdentifier> noAuthors = api.relationships().readToMany(emptyAuthorsJson);
```

`noAuthors` is an empty list (`List.of()`), never `null`.

`readToOne` and `readToMany` enforce cardinality rather than converting across shapes: `readToOne`
rejects `"data": []`, and `readToMany` rejects `"data": null` or a single identifier object.
`api.relationships()` reads and writes data-only linkage documents; for top-level `links` or `meta`
on a linkage document, use `api.documents()` below.

## Preserve document state

`api.resources()` binds a single resource object (`readOne`) or a resource array (`readMany`) to a
flat Java DTO. A DTO cannot represent every valid JSON:API document—such as a response whose
primary `data` is `null` (`GET /articles/1/author` when no author is assigned) or a metadata-only
document where `data` is omitted.

`api.documents()` reads and writes the immutable core `JsonApiDocument` model so your application
can distinguish **absent members**, **explicit `null`**, and **empty collections**:

| Wire state | Example JSON | `JsonApiDocument` state | `Relationship` state |
|------------|--------------|-------------------------|----------------------|
| Absent `data` | `{"meta": {"total": 0}}` | `document.data()` is `null` (`hasDataMember()` is `false`) | `relationship.data()` is `null` (`hasDataMember()` is `false`) |
| Explicit `null` | `{"data": null}` | `DocumentData.NullData` (`hasDataMember()` is `true`) | `RelationshipData.NullLinkage` |
| Empty collection | `{"data": []}` | `DocumentData.ResourceCollection(List.of())` or `DocumentData.IdentifierCollection(List.of())` | `RelationshipData.IdentifierCollectionLinkage.empty()` |

Suppose an endpoint returns `responseBody` containing explicit-null primary `data`:

```json
{
  "data": null
}
```

Read it with `DocumentReadContext.resourceDefaults()`:

```java
import com.kazforge.jsonapi.core.model.DocumentData;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.document.DocumentReadContext;

DocumentReadContext resourceContext = DocumentReadContext.resourceDefaults();
JsonApiDocument document = api.documents().read(responseBody, resourceContext);
DocumentData primaryData = document.data();
```

`document.hasDataMember()` is `true`, and `primaryData` is `DocumentData.NullData.INSTANCE`
(`DocumentData.NullData`), not Java `null`. Writing `document` back preserves the explicit-null
`data` member:

```java
String responseJson = api.documents().write(document);
```

`responseJson` has the same `{"data": null}` shape as `responseBody`.

By contrast, when a document omits `data` and carries only top-level `meta` (`metaOnlyBody`):

```json
{
  "meta": {
    "total": 0
  }
}
```

```java
JsonApiDocument metaDocument = api.documents().read(metaOnlyBody, resourceContext);
```

`metaDocument.hasDataMember()` is `false`, `metaDocument.data()` is `null`, and
`metaDocument.meta()` contains `"total": 0`.

`api.documents().read(...)` requires an explicit `DocumentReadContext` because a JSON object with
only `"type"` and `"id"`—or an empty array `[]`—is valid both as a resource with no attributes and
as a resource identifier. For a relationship-endpoint response (`linkageBody`) that also carries
top-level `links`:

```json
{
  "data": {
    "type": "people",
    "id": "p1"
  },
  "links": {
    "self": "/articles/1/relationships/author",
    "related": "/articles/1/author"
  }
}
```

select identifier decoding (`PrimaryDataKind.RESOURCE_IDENTIFIER`) and relationship-endpoint
validation (`PrimaryDataContext.RELATIONSHIP`) together:

```java
import com.kazforge.jsonapi.core.aggregate.ValidationContext;
import com.kazforge.jsonapi.core.validation.PrimaryDataContext;
import com.kazforge.jsonapi.document.PrimaryDataKind;

ValidationContext relationshipValidation = ValidationContext.defaults()
    .withPrimaryDataContext(PrimaryDataContext.RELATIONSHIP);
DocumentReadContext linkageContext = DocumentReadContext.of(
    relationshipValidation,
    PrimaryDataKind.RESOURCE_IDENTIFIER
);

JsonApiDocument linkage = api.documents().read(linkageBody, linkageContext);
```

`linkage.data()` is a `DocumentData.SingleIdentifier` for `people` / `p1`, and `linkage.links()`
preserves the `self` and `related` links.

`DocumentReadContext.identifierDefaults()` selects identifier decoding while keeping the default
`RESOURCE` endpoint validation rules; select `PrimaryDataContext.RELATIONSHIP` explicitly as above
for relationship endpoints. `api.documents().write(...)` validates with
`DocumentUsage.RESPONSE_OR_OTHER`. Use [resource operations](resources.md) to author create or
update documents, or a Jackson-specific document writer when you need a custom validation context.

## Links-only and meta-only relationships

DTO-mapped relationships always carry `data` (even when `null` or `[]`). For a response that
intentionally omits relationship `data` and exposes only navigation links or relationship metadata,
construct the relationship in the core model:

```java
import com.kazforge.jsonapi.core.aggregate.ValidationContext;
import com.kazforge.jsonapi.core.model.DocumentData;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.core.model.Link;
import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.core.model.Relationship;
import com.kazforge.jsonapi.core.model.Relationships;
import com.kazforge.jsonapi.core.model.ResourceObject;
import java.util.Map;

Link authorUrl = new Link.StringLink("/articles/1/author");
Links authorLinks = Links.ofLinks(Map.of("related", authorUrl));
Relationship authorLink = Relationship.linkOnly(authorLinks);
Relationships relationships = Relationships.ofRelationships(
    Map.of("author", authorLink)
);

ResourceObject articleResource = ResourceObject.of("articles", "1")
    .withRelationships(relationships);
DocumentData articleData = new DocumentData.SingleResource(articleResource);
JsonApiDocument authorDocument = JsonApiDocument.withData(articleData);

String linksOnlyJson = api.documents().write(authorDocument);
```

`linksOnlyJson` contains the article with a links-only `author` relationship:

```json
{
  "data": {
    "type": "articles",
    "id": "1",
    "relationships": {
      "author": {
        "links": {
          "related": "/articles/1/author"
        }
      }
    }
  }
}
```

Here, `authorLink.hasDataMember()` is `false` and `authorLink.data()` is `null`.

For relationship metadata without linkage or links, use `Relationship.metaOnly(...)` instead:

```java
Meta visibility = Meta.of(Map.of("visible", false));
Relationship authorMeta = Relationship.metaOnly(visibility);
ResourceObject metaArticle = ResourceObject.of("articles", "1")
    .withRelationships(Relationships.ofRelationships(Map.of("author", authorMeta)));

String metaOnlyJson = api.documents().write(
    JsonApiDocument.withData(new DocumentData.SingleResource(metaArticle))
);
```

`metaOnlyJson` contains:

```json
{
  "data": {
    "type": "articles",
    "id": "1",
    "relationships": {
      "author": {
        "meta": {
          "visible": false
        }
      }
    }
  }
}
```

Core constructors check local invariants (for example, a links-only relationship requires a
non-pagination link such as `self` or `related`), and `api.documents().write(...)` validates
whole-document rules before serializing. With only `jsonapi-java-core` and no adapter, run
aggregate validation explicitly on the same model:

```java
import com.kazforge.jsonapi.core.aggregate.JsonApiDocumentValidator;

JsonApiDocumentValidator validator = new JsonApiDocumentValidator();
ValidationContext validation = ValidationContext.defaults();

validator.validate(authorDocument, validation);
```

Neither core construction nor document validation executes application business logic, resolves
URIs, or checks persisted state.

Links-only and meta-only relationships are supported response and general document forms, **not**
valid replacements for supplied primary relationships in create (`readCreate`) or update
(`readPatch`) requests. Supplied relationships in create and update requests must include `data`
(otherwise validation fails with `RELATIONSHIP_DATA_REQUIRED`). Additive
[resource link decorators](configuration.md#resource-link-decorators) can add `links` to an
existing mapped relationship, but they cannot create a data-less relationship or remove linkage.

## Error documents

An error document carries top-level `errors` instead of `data`. Core construction and validation
enforce JSON:API error-document structure (`errors` and `data` cannot coexist, each `ErrorObject`
requires at least one standard member, and `source.pointer` must be valid RFC 6901 syntax) without
running application business rules. Your application chooses the HTTP status, error code, safe
message, and response policy.

Suppose a client supplies `"title": ""` in `/data/attributes/title` and your application requires a
non-empty title. Because `/data/attributes/title` identifies an existing value in the request, it is
a valid JSON:API `source.pointer`:

```java
import com.kazforge.jsonapi.core.model.ErrorObject;
import com.kazforge.jsonapi.core.model.ErrorSource;
import com.kazforge.jsonapi.core.model.JsonApiDocument;

ErrorSource source = ErrorSource.builder()
    .pointer("/data/attributes/title")
    .build();

ErrorObject error = ErrorObject.builder()
    .status("422")
    .code("title-required")
    .title("Title is required")
    .source(source)
    .build();
JsonApiDocument errorDocument = JsonApiDocument.withError(error);

String errorJson = api.documents().write(errorDocument);
```

`errorJson` contains:

```json
{
  "errors": [
    {
      "status": "422",
      "code": "title-required",
      "title": "Title is required",
      "source": {
        "pointer": "/data/attributes/title"
      }
    }
  ]
}
```

Read error documents through `api.documents()`:

```java
import com.kazforge.jsonapi.document.DocumentReadContext;
import java.util.List;

DocumentReadContext responseContext = DocumentReadContext.resourceDefaults();
JsonApiDocument readErrors = api.documents().read(errorJson, responseContext);
List<ErrorObject> reportedErrors = readErrors.errors();
```

`reportedErrors` contains the `title-required` error, and `readErrors.hasDataMember()` is `false`.

`ErrorSource` checks RFC 6901 pointer syntax only; the library does not verify that the pointer
resolves against the request payload or convert library exceptions into HTTP error responses
automatically.

## Diagnostics

See [Diagnostics](diagnostics.md) for the four exception families, stable codes, location semantics,
and selective application-owned JSON:API error attribution. A diagnostic path is not automatically
a valid request `source.pointer`; public detail and HTTP policy stay with the application.

## Choose an application or Jackson-specific API {#ordinary-or-advanced}

Use `JsonApi` for the operations shown in this guide. The Jackson 3 adapter also exposes
Jackson-specific readers, writers, mappers, and binders for direct control over types, validation,
and mapping policy:

| Need | Use |
|------|-----|
| Resource DTOs of one declared type, linkage, raw documents, typed PATCH | `JsonApi` application API |
| Exact absent/null/empty state, links-only relationships, errors | `api.documents()` with core values and explicit read context |
| Parameterized Jackson `JavaType`, custom codec validation, per-call mapping policy | Jackson-specific readers, writers, mappers, binders |
| Primary/included DTOs of different resource types | Jackson-specific domain-document reader with explicit `ResourceTypeRegistry` |

<!-- x-release-please-start-version -->
The Jackson-specific factories are on
[`JsonApiJackson3`](https://www.javadocs.dev/com.kazforge/jsonapi-java-jackson3/0.4.1/com/kazforge/jsonapi/jackson3/JsonApiJackson3.html).
Typed document readers bind included resources independently, not into DTO relationships. Jackson 3
APIs follow Jackson 3's unchecked exception model; `JsonApi` stream operations wrap `IOException` in
`UncheckedIOException`. Caller-owned sources and sinks remain open.
<!-- x-release-please-end -->
