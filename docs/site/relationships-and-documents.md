---
title: "JSON:API relationships, linkage, and documents"
description: "Read and write JSON:API relationship linkage and error documents in Java. Preserve absent, null, and empty document states without loading related resources."
---

# JSON:API relationships, linkage, and documents {#relationships-and-documents}

Use the configured `JsonApi api` from [getting started](getting-started.md#configure-a-runtime).

## Relationship linkage documents

`api.relationships()` reads and writes identifiers as primary `data`, rather than fetching related
objects or returning their DTOs. Write a to-one linkage document for an existing author:

```java
import com.kazforge.jsonapi.core.model.ResourceIdentifier;

ResourceIdentifier author = ResourceIdentifier.of("people", "p1");
String authorJson = api.relationships().writeToOne(author);
```

Result:

```json
{
  "data": {
    "type": "people",
    "id": "p1"
  }
}
```

Read it back as linkage, without a resource-type registry:

```java
ResourceIdentifier linkedAuthor = api.relationships().readToOne(authorJson);
```

`linkedAuthor` identifies `people` / `p1`.

### Null to-one linkage

A null identifier represents no to-one linkage:

```java
String noAuthorJson = api.relationships().writeToOne(null);
```

Result:

```json
{
  "data": null
}
```

Reading this document with `readToOne` returns Java null.

### To-many linkage

Write the selected identifiers as a collection:

```java
import java.util.List;

List<ResourceIdentifier> authors = List.of(author);
String authorsJson = api.relationships().writeToMany(authors);
```

Result:

```json
{
  "data": [
    {
      "type": "people",
      "id": "p1"
    }
  ]
}
```

Read a to-many document with the matching operation:

```java
List<ResourceIdentifier> linkedAuthors = api.relationships().readToMany(authorsJson);
```

The list contains the `people` / `p1` identifier. An empty to-many write is a different state from null:

```java
String emptyAuthorsJson = api.relationships().writeToMany(List.of());
```

Result:

```json
{
  "data": []
}
```

`readToMany` returns an empty list for this document. To-one and to-many reads reject the other
cardinality rather than converting it. For top-level links/meta on a linkage document, use
`api.documents()` or a Jackson-specific document writer.

## Preserve document state

`api.documents().read` needs a `DocumentReadContext` that selects resources or identifiers and the
validation rules to apply. A type/id-only object or empty array cannot distinguish those choices on
its own. For a response body (`responseBody`) containing explicit-null primary data:

```json
{
  "data": null
}
```

```java
import com.kazforge.jsonapi.core.model.DocumentData;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.document.DocumentReadContext;

DocumentReadContext resourceContext = DocumentReadContext.resourceDefaults();
JsonApiDocument document = api.documents().read(responseBody, resourceContext);
DocumentData primaryData = document.data();
```

`primaryData` is a `DocumentData.NullData`, not Java null. Writing the document preserves the
explicit-null member:

```java
String responseJson = api.documents().write(document);
```

The result has the same JSON shape as the input above.

In the core model, a null member reference means the member is absent. Explicit-null `data` uses
`DocumentData.NullData` (or `RelationshipData.NullLinkage` inside a relationship). Empty collections
or wrapper objects remain present. A flat DTO cannot represent every one of these states.

For this relationship-endpoint response body (`linkageBody`):

```json
{
  "data": {
    "type": "people",
    "id": "p1"
  }
}
```

Select identifier decoding and relationship-endpoint validation separately:

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

`linkage.data()` is a `DocumentData.SingleIdentifier` for `people` / `p1`.

`identifierDefaults()` selects identifier decoding but keeps the default validation rules; select
relationship-endpoint validation explicitly as above. `api.documents().write` validates with
`DocumentUsage.RESPONSE_OR_OTHER` (response or other document use). Use
[resource operations](resources.md) to author create/update documents, or a Jackson-specific document
writer when you need a custom validation context.

## Links-only and meta-only relationships

DTO-mapped relationships always carry `data`. For a response that intentionally leaves it out,
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

Result:

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

For relationship metadata without linkage, use a meta-only value instead:

```java
Meta visibility = Meta.of(Map.of("visible", false));
Relationship authorMeta = Relationship.metaOnly(visibility);
```

That relationship object is:

```json
{
  "meta": {
    "visible": false
  }
}
```

Core constructors check local invariants; document writers also validate whole-document rules.
With only `jsonapi-java-core` and no adapter, run aggregate validation explicitly on the same model:

```java
import com.kazforge.jsonapi.core.aggregate.JsonApiDocumentValidator;

JsonApiDocumentValidator validator = new JsonApiDocumentValidator();
ValidationContext validation = ValidationContext.defaults();

validator.validate(authorDocument, validation);
```

Links-only/meta-only relationships are general document forms, not valid replacements for supplied
primary relationships in create/update requests. Those requests require relationship `data`.
Additive mapping decorators can add links to an existing relationship; they cannot create a
data-less relationship or remove linkage.

## Error documents

An error document has `errors`, not `data`. Your application chooses the status, code, safe detail,
and HTTP response policy:

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

Result:

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

`reportedErrors` contains the title-required error; the document has no `data` member.

`source.pointer` is syntax-checked; the library does not resolve it against the request payload.
It does not convert every library exception into an HTTP error automatically.

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
[`JsonApiJackson3`](https://www.javadocs.dev/com.kazforge/jsonapi-java-jackson3/0.4.0/com/kazforge/jsonapi/jackson3/JsonApiJackson3.html).
Typed document readers bind included resources independently, not into DTO relationships. Jackson 3
APIs follow Jackson 3's unchecked exception model; `JsonApi` stream operations wrap `IOException` in
`UncheckedIOException`. Caller-owned sources and sinks remain open.
<!-- x-release-please-end -->
