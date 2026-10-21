---
title: "Parse JSON:API queries, includes, and sparse fieldsets"
description: "Parse JSON:API include, fields, and sort parameters in Java, then apply representation selection and policy. Query execution stays with your application."
---

# Parse JSON:API queries, includes, and sparse fieldsets {#query-and-representation}

Add `com.kazforge:jsonapi-java-query` at the same version as your adapter. This module has no
Jackson or HTTP framework dependency.

Turning an incoming query string into a shaped JSON:API response follows four steps:

1. **Parse** the raw query string or decoded parameter map into a `JsonApiQuery`.
2. **Extract** per-request `query.selection()` (`include` paths and `fields[TYPE]` sparse fieldsets), while handling `sort`, `page`, and `filter` in your application's query layer.
3. **Pass** that `RepresentationSelection` in `ResourceWriteOptions` for one write call.
4. **Serialize** your supplied DTOs through a `JsonApi` runtime whose `RepresentationPolicy` permits the requested includes and fields.

The examples below reuse `Person`, `ArticleWithAuthor`, and `ArticleView` from
[Resources](resources.md#write-relationships-read-linkage) and `selectedApi` from the
[representation policy recipe](configuration.md#representation-policy). Output JSON is formatted
for readability.

## Parse a request

Suppose an HTTP request arrives with this raw query string (`requestQuery`), and your application
parses it with `JsonApiQueryParser`:

```java
import com.kazforge.jsonapi.query.JsonApiQuery;
import com.kazforge.jsonapi.query.JsonApiQueryParser;

String requestQuery =
    "?include=author&fields[articles]=title,author&sort=-title&page[number]=2&filter[status]=published";

JsonApiQueryParser parser = new JsonApiQueryParser();
JsonApiQuery query = parser.parseRaw(requestQuery);
```

`query` exposes the parsed representation selection, sort directives, and opaque parameter lists:

| `JsonApiQuery` accessor | Value for `requestQuery` |
|-------------------------|--------------------------|
| `query.selection().includePaths()` | `[IncludePath.of("author")]` (`includeRequested()` is `true`) |
| `query.selection().fieldsets()` | `Map.of("articles", List.of("title", "author"))` |
| `query.sortFields()` | `List.of(SortField.descending("title"))` |
| `query.pageParameters()` | `Map.of("page[number]", List.of("2"))` |
| `query.filterParameters()` | `Map.of("filter[status]", List.of("published"))` |
| `query.unprocessedParameters()` | `Map.of()` |

These are parsed request values, not an executed persistence query. Parsing does not run sorting,
filtering, pagination, or database queries.

When an HTTP framework has already URL-decoded the query parameters into a multimap, call
`parseDecoded` instead of `parseRaw` so values are not URL-decoded twice:

```java
import java.util.List;
import java.util.Map;

Map<String, List<String>> parameters = Map.of(
    "include", List.of("author"),
    "fields[articles]", List.of("title,author")
);
JsonApiQuery selected = parser.parseDecoded(parameters);
```

`selected.selection()` carries the same `include=author` and `fields[articles]=title,author`
selection as `query.selection()` above.

`include`, each `fields[TYPE]`, and `sort` require **one value occurrence**, with comma-separated
tokens inside that value. Names are exact JSON:API wire tokens, not trimmed Java property names.
Explicit empty values (`include=` or `fields[articles]=`) remain distinct from omitting the
parameter. `QueryAllowList` can reject unpermitted include paths, fieldset names, or directionless
sort tokens during parsing, but a successful parse grants no authorization to access a resource or
field.

## Apply selection with policy

Runtime policy and per-write selection have separate scopes:

- **`RepresentationPolicy`** is configured once on `selectedApi` (see
  [Representation policy](configuration.md#representation-policy)), allowing `articles` to include
  `author` and permitting the fields `articles.title`, `articles.author`, and `people.name`.
- **`RepresentationSelection`** (`selected.selection()`) belongs to one write operation and is
  passed via `ResourceWriteOptions`.

### Include a related resource

Suppose your application has already loaded article `"1"` and its author `ada`. Pass
`selected.selection()` in `ResourceWriteOptions` when writing the response:

```java
import com.kazforge.jsonapi.api.ResourceWriteOptions;

Person ada = new Person("p1", "Ada");
ArticleWithAuthor article = new ArticleWithAuthor(
    "1",
    "Working with JSON:API",
    ada
);
ResourceWriteOptions options = ResourceWriteOptions.defaults()
    .withSelection(selected.selection());

String json = selectedApi.resources().writeOne(article, options);
```

`json` contains both the primary article in `data` and the author resource in `included`:

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
  },
  "included": [
    {
      "type": "people",
      "id": "p1",
      "attributes": {
        "name": "Ada"
      }
    }
  ]
}
```

Compare this with the default write in
[Resources](resources.md#write-relationships-read-linkage):

- **Relationship linkage (`data.relationships.author.data`):** Written whenever the `author`
  relationship is selected on `articles`, even without `include=author`. It carries only the
  target's `type` (`"people"`) and `id` (`"p1"`).
- **Included resource (`included[0]`):** Added at the top level only because the request selected
  `include=author` and `selectedApi`'s `IncludePolicy` allowed `articles` → `author`. It serializes
  the `Person` instance (`ada`) that your application supplied on `article`.

Inclusion never fetches related objects automatically; your application loads permitted objects
before calling `writeOne`.

When reading that compound document back with `readOneDocument`, the primary DTO and `included`
resources stay separate:

```java
import com.kazforge.jsonapi.api.ResourceDocument;
import com.kazforge.jsonapi.core.model.ResourceObject;

ResourceDocument<ArticleView> result = selectedApi.resources().readOneDocument(
    json,
    ArticleView.class
);
ArticleView primaryArticle = result.resource();
List<ResourceObject> included = result.included();
```

`primaryArticle.author()` still contains only the `ResourceIdentifier` for `"people"` / `"p1"`.
`included` contains the separate core `ResourceObject` carrying Ada's `"name"` attribute; the
binder does not inject `included` entries into DTO relationship fields.

!!! warning "Selection is not permission"
    An include request cannot override application representation policy. Policy is not a substitute
    for endpoint/resource/field authorization. Do not copy an unrestricted `allowAll()` policy into a
    public endpoint merely to make a query work.

### Omit attributes or relationships with sparse fieldsets

A sparse fieldset (`fields[TYPE]`) filters the mapped attributes and relationships written for every
resource of that JSON:API `type` across both `data` and `included`. Resource types not mentioned in
`fields` keep all of their mapped attributes and relationships.

When a client requests only the article's `title` and omits `author` (`?fields[articles]=title`,
with no `include` parameter):

```java
JsonApiQuery titleOnlyQuery = parser.parseRaw("?fields[articles]=title");
ResourceWriteOptions titleOnlyOptions = ResourceWriteOptions.defaults()
    .withSelection(titleOnlyQuery.selection());

String titleOnlyJson = selectedApi.resources().writeOne(
    article,
    titleOnlyOptions
);
```

Because `author` is omitted from `fields[articles]`, the writer skips `article.author()` and omits
`"relationships"` from `data`:

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

Conversely, selecting only the relationship (`?fields[articles]=author`) omits `title`. Because no
attributes remain selected on `articles`, `"attributes"` is omitted and only `"relationships"` is
written:

```java
JsonApiQuery authorOnlyQuery = parser.parseRaw("?fields[articles]=author");
ResourceWriteOptions authorOnlyOptions = ResourceWriteOptions.defaults()
    .withSelection(authorOnlyQuery.selection());

String authorOnlyJson = selectedApi.resources().writeOne(
    article,
    authorOnlyOptions
);
```

```json
{
  "data": {
    "type": "articles",
    "id": "1",
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

An explicitly empty fieldset (`?fields[articles]=`) selects neither attributes nor relationships,
writing only `"type": "articles"` and `"id": "1"` inside `data`. Resource identity (`type`, `id`,
`lid`) and `@JsonApiMeta` resource metadata are outside sparse-fieldset filtering.

### Combine `include` with a sparse fieldset

Inclusion and sparse fieldsets are evaluated per resource type. A request can include `author`
while omitting `author` from `fields[articles]`:

```java
JsonApiQuery combinedQuery = parser.parseRaw(
    "?include=author&fields[articles]=title"
);
ResourceWriteOptions combinedOptions = ResourceWriteOptions.defaults()
    .withSelection(combinedQuery.selection());

String combinedJson = selectedApi.resources().writeOne(
    article,
    combinedOptions
);
```

`fields[articles]=title` removes `"relationships"` from the primary article, while `include=author`
still traverses `article.author()` and writes Ada's `"people"` resource in `included` (if the query
also supplied `fields[people]=name` or `fields[people]=`, that fieldset would filter the included
`"people"` resource independently):

```json
{
  "data": {
    "type": "articles",
    "id": "1",
    "attributes": {
      "title": "Working with JSON:API"
    }
  },
  "included": [
    {
      "type": "people",
      "id": "p1",
      "attributes": {
        "name": "Ada"
      }
    }
  ]
}
```

During `writeOne`, the mapper records that `fields[articles]=title` removed the `"author"` linkage,
so write-time validation permits `"people"` / `"p1"` in `included` under the JSON:API 1.1
sparse-fieldset exemption to full linkage. When reading a raw JSON string later, default document
validation has no record of the write-time fieldset and rejects unlinked `included` resources with
`FULL_LINKAGE_VIOLATION` unless you read the document through `api.documents().read(...)` with
`ValidationContext.defaults().withSparseFieldsetLinkageExemptions(...)` (see
[Preserve document state](relationships-and-documents.md#preserve-document-state)).

## What stays with the application

| Parameter | Library result | Application work |
|-----------|----------------|------------------|
| `include`, `fields[TYPE]` | Representation selection | Authorize, load permitted data, apply runtime policy |
| `sort` | Ordered field/direction values | Map permitted names to actual query ordering |
| `page`, `filter`, unknown parameters | Ordered opaque value lists | Define semantics, validate values, execute queries |

Parsing preserves repeated opaque values and encounter order. It does not calculate page links,
interpret filters, run sorting, or choose an HTTP status. Query failures use `JsonApiQueryException`
with `diagnostic()` and an optional `parameterName()`, separate from document/mapping failures.
See [query attribution](diagnostics.md#query-parameters) for decoded parameter names and
[application-owned error responses](diagnostics.md#build-an-application-owned-error-response) for
selective use of `source.parameter`.

<!-- x-release-please-start-version -->
See [`JsonApiQueryParser`](https://www.javadocs.dev/com.kazforge/jsonapi-java-query/0.4.0/com/kazforge/jsonapi/query/JsonApiQueryParser.html)
and [`RepresentationPolicy`](https://www.javadocs.dev/com.kazforge/jsonapi-java-api/0.4.0/com/kazforge/jsonapi/representation/RepresentationPolicy.html)
for exact parsing and policy contracts.
<!-- x-release-please-end -->
