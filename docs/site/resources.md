---
title: "Read and write JSON:API resources as Java DTOs"
description: "Map JSON:API create requests and responses to Java DTOs. Add relationship linkage, resource collections, and document-level metadata."
---

# Read and write JSON:API resources as Java DTOs {#resources}

[Getting started](getting-started.md) read and wrote an identified article. Now follow an article
from an incoming create request to a response, then add an author, collections, and metadata.

Reuse `JsonApi api` from [runtime setup](getting-started.md#configure-a-runtime) and the
[`Article` record and annotations](getting-started.md#read-and-write-an-article).
JSON input names such as `createBody` refer to Java strings containing the displayed document.
Output JSON is formatted for readability.

## Create, id, and lid

### Read an incoming create request

A client can ask to create an article without supplying an `id`. This request body (`createBody`)
contains only the resource type and its attributes:

```json
{
  "data": {
    "type": "articles",
    "attributes": {
      "title": "Working with JSON:API"
    }
  }
}
```

Use `readCreate` to validate the document as a create request and bind it to the DTO:

```java
Article requested = api.resources().readCreate(createBody, Article.class);
```

`requested.id()` is null and `requested.title()` is `"Working with JSON:API"`.
`readOne` is for reading a response resource, which requires an `id`; it rejects this ID-less
request. Reading a create request does not save an article or assign an identifier.

### Write and read the created resource

Your application owns validation of business rules, authorization, and persistence. Suppose it
creates the article and assigns id `"1"`. Represent that result as an identified DTO:

```java
Article created = new Article("1", requested.title());
String responseJson = api.resources().writeOne(created);
```

The response document is:

```json
{
  "data": {
    "type": "articles",
    "id": "1",
    "attributes": {
      "title": "Working with JSON:API"
    }
  }
}
```

A client reads that response with `readOne`:

```java
Article saved = api.resources().readOne(responseJson, Article.class);
```

`saved.id()` is `"1"`. Both response reads and response writes require the resource's `id`.

### Author a create document

When your Java application is the client, use `writeCreateDocument` to produce the request body:

```java
Article draft = new Article(null, "Working with JSON:API");
String createJson = api.resources().writeCreateDocument(draft);
```

`createJson` has the same JSON as `createBody` above: no `id` or `lid` is needed. This authors a
document; it does not send a request or create a persisted resource.

### Optional local identifiers

A `lid` is a document-scoped local identifier, separate from the resource's `id`. To use one,
declare its role explicitly:

```java
import com.kazforge.jsonapi.annotation.JsonApiLocalId;

@JsonApiResource(type = "articles")
public record NewArticle(
    @JsonApiId String id,
    @JsonApiLocalId String localId,
    @JsonApiAttribute String title
) {}
```

Write a draft with a local identifier:

```java
NewArticle localDraft = new NewArticle(null, "draft-1", "Working with JSON:API");
String localCreateJson = api.resources().writeCreateDocument(localDraft);
```

The local identifier appears as `lid`, not `id`:

```json
{
  "data": {
    "type": "articles",
    "lid": "draft-1",
    "attributes": {
      "title": "Working with JSON:API"
    }
  }
}
```

```java
NewArticle localRequest = api.resources().readCreate(localCreateJson, NewArticle.class);
```

`localRequest.id()` is null; `localRequest.localId()` is `"draft-1"`. Neither identifier role
falls back to the other. A supplied `lid` can identify a matching primary self-reference, but
create allowances are not a nested-create protocol: included resources and unrelated linkage need ids.

## Write relationships, read linkage

Once the article has an author, its document needs a relationship. On writes, the DTO can carry
an already available `Person`; on reads, use `ResourceIdentifier` for the author's linkage.
These two article shapes make that distinction explicit. Each public record below is a separate
Java type, normally declared in its own file:

```java
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;

@JsonApiResource(type = "people")
public record Person(
    @JsonApiId String id,
    @JsonApiAttribute String name
) {}

@JsonApiResource(type = "articles")
public record ArticleWithAuthor(
    @JsonApiId String id,
    @JsonApiAttribute String title,
    @JsonApiRelationship Person author
) {}

@JsonApiResource(type = "articles")
public record ArticleView(
    @JsonApiId String id,
    @JsonApiAttribute String title,
    @JsonApiRelationship ResourceIdentifier author
) {}
```

Write the article with an already available author object:

```java
Person ada = new Person("p1", "Ada");
ArticleWithAuthor article = new ArticleWithAuthor(
    "1",
    "Working with JSON:API",
    ada
);

String articleJson = api.resources().writeOne(article);
```

The default write emits linkage, but no `included` author:

```json
{
  "data": {
    "type": "articles",
    "id": "1",
    "attributes": {
      "title": "Working with JSON:API"
    },
    "relationships": {
      "author": {
        "data": {
          "type": "people",
          "id": "p1"
        }
      }
    }
  }
}
```

Read that document into the flat application view:

```java
ArticleView articleView = api.resources().readOne(
    articleJson,
    ArticleView.class
);
ResourceIdentifier author = articleView.author();
```

`author` identifies resource type `people`, id `p1`. It does not contain Ada's name, even if
the input document supplies the person in `included`.

Built-in read targets include `ResourceIdentifier` and its optional/collection/array forms. Custom
relationship targets need an [explicit linkage mapper](configuration.md#custom-relationship-linkage-targets);
mapping does not look up persisted people.
Use [selection and policy](query-and-representation.md) to include author resources on writes.

!!! warning "Mapped relationships carry data"
    Every selected DTO relationship has `data`. A null to-one value becomes
    `"data": null`; an empty to-many value becomes `"data": []`. An empty optional is not a
    links-only relationship. Use the [document path](relationships-and-documents.md#links-only-and-meta-only-relationships)
    when relationship `data` must be absent.

## Collections and shape

For an article listing, write a list of DTOs. Here the simple `Article` shape keeps the listing
to identities and titles:

```java
import java.util.List;

List<Article> articles = List.of(
    created,
    new Article("2", "Choosing resource relationships")
);
String collectionJson = api.resources().writeMany(articles);
```

The document's `data` is now an array:

```json
{
  "data": [
    {
      "type": "articles",
      "id": "1",
      "attributes": {
        "title": "Working with JSON:API"
      }
    },
    {
      "type": "articles",
      "id": "2",
      "attributes": {
        "title": "Choosing resource relationships"
      }
    }
  ]
}
```

Read it with the collection operation:

```java
List<Article> listed = api.resources().readMany(collectionJson, Article.class);
```

`listed` contains articles `1` and `2`, in that order. An empty list writes an empty `data` array
and reads back as an empty list.

`readOne` requires one resource object; `readMany` requires a resource array, including `[]`.
Neither coerces null, absent `data`, linkage documents, or errors into a DTO or an empty list.
For those states, use [documents](relationships-and-documents.md#preserve-document-state).

## Top-level state

To attach a request identifier to an article response, put it in document-level `meta` using an
envelope. The same envelope API can supply top-level links or an explicit `jsonapi` object:

```java
import com.kazforge.jsonapi.api.ResourceWriteOptions;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.document.DocumentEnvelope;
import java.util.Map;

Meta requestMeta = Meta.of(Map.of("request", "req-7"));
DocumentEnvelope envelope = DocumentEnvelope.ofMeta(requestMeta);
ResourceWriteOptions options = ResourceWriteOptions.defaults()
    .withEnvelope(envelope);

String envelopeJson = api.resources().writeOne(created, options);
```

This adds a top-level `meta` object to the resource document:

```json
{
  "data": {
    "type": "articles",
    "id": "1",
    "attributes": {
      "title": "Working with JSON:API"
    }
  },
  "meta": {
    "request": "req-7"
  }
}
```

Read the DTO and document-level state together:

```java
import com.kazforge.jsonapi.api.ResourceDocument;

ResourceDocument<Article> result = api.resources().readOneDocument(
    envelopeJson,
    Article.class
);
Article primaryArticle = result.resource();
Meta documentMeta = result.meta();
```

`primaryArticle` has id `1`; `documentMeta` carries the request identifier `"req-7"`.
For a collection with document-level metadata, use `readManyDocument` instead of `readMany`.

The typed document result carries `included` as validated core resources, rather than binding them
to DTOs.
Resource meta, relationship meta, and identifier meta are different locations; an envelope supplies
only top-level members. To preserve absent, explicit-null, and empty document members, use
[`api.documents()`](relationships-and-documents.md#preserve-document-state).

## Author an update document

When your Java code authors an outbound update request from a DTO, use `writeUpdateDocument` with
the resource's `type` and `id` and, when known, the expected endpoint identity:

```java
import com.kazforge.jsonapi.core.validation.EndpointIdentity;

EndpointIdentity target = new EndpointIdentity("articles", "1");
String updateJson = api.resources().writeUpdateDocument(created, target);
```

This produces the same JSON as `responseJson` above, validated as an update request against
`target` (an identity mismatch fails validation). It serializes the selected DTO members rather
than calculating an entity diff or omitting `null` fields.

To read and bind an incoming partial update while distinguishing omitted members, explicit `null`,
and supplied values, use `api.patches().readPatch(...)` in the [PATCH guide](patches.md). Neither
operation mutates persisted state.

## Naming and failures

Use JSON:API member names in selections, not Java field names. Configure renaming and conversion
on Jackson before creating the runtime; role annotations do not override Jackson-ignored properties.
DTO mapping is not a lossless representation of member presence.

<!-- x-release-please-start-version -->
Reads validate the whole document before binding. Malformed/invalid input and DTO mapping problems
remain [distinct failure families](relationships-and-documents.md#diagnostics). The
[`JsonApiResources`](https://www.javadocs.dev/com.kazforge/jsonapi-java-api/0.4.1/com/kazforge/jsonapi/api/JsonApiResources.html)
source API documentation owns generic-type and stream overload contracts; caller-supplied streams
remain open.
<!-- x-release-please-end -->
