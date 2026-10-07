# Query and representation

Add `com.kazforge:jsonapi-java-query` at the same version as your adapter. This module is independent
of Jackson major and HTTP framework. Use the article/people write model and `ArticleView` from
[resources](resources.md#write-relationships-read-linkage).

## Parse a request

For a query string (`requestQuery`) such as:

```text
?include=author&fields[articles]=title,author&sort=-title&page[number]=2&filter[status]=published
```

Parse the requested selection and opaque parameters:

```java
import com.kazforge.jsonapi.query.JsonApiQuery;
import com.kazforge.jsonapi.query.JsonApiQueryParser;

JsonApiQueryParser parser = new JsonApiQueryParser();
JsonApiQuery query = parser.parseRaw(requestQuery);
```

Result:

```text
include:          author
fields[articles]: title, author
sort:             title (descending)
page[number]:     [2]
filter[status]:   [published]
```

These are requested values, not an executed persistence query.

For framework parameters that are already decoded, use `parseDecoded`; do not URL-decode twice:

```java
import java.util.List;
import java.util.Map;

Map<String, List<String>> parameters = Map.of(
    "include", List.of("author"),
    "fields[articles]", List.of("title,author")
);
JsonApiQuery selected = new JsonApiQueryParser().parseDecoded(parameters);
```

`include`, each `fields[TYPE]`, and `sort` require **one value occurrence**, with comma-separated
tokens inside that value. Names are exact JSON:API tokens, not trimmed Java property names.
Explicit `include=` or `fields[articles]=` stays distinct from omission. `QueryAllowList` can reject
unpermitted include, field, or sort tokens, but a successful parse grants no access to a resource or field.

## Apply selection with policy

Configure inclusion once on the runtime. The following Jackson 3 example permits only the article's
`author` relationship; for Jackson 2 use its corresponding mapper/factory imports.

```java
import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.api.ResourceWriteOptions;
import com.kazforge.jsonapi.jackson3.JsonApiJackson3;
import com.kazforge.jsonapi.representation.IncludePolicy;
import com.kazforge.jsonapi.representation.RelationshipAllowance;
import com.kazforge.jsonapi.representation.RepresentationPolicy;
import java.util.Set;
import tools.jackson.databind.json.JsonMapper;

RelationshipAllowance authorAllowance = RelationshipAllowance.of("articles", "author");
IncludePolicy allowedIncludes = IncludePolicy.allowing(Set.of(authorAllowance));

RepresentationPolicy policy = RepresentationPolicy.defaults()
    .withIncludePolicy(allowedIncludes)
    .withMaxIncludeDepth(1)
    .withMaxIncludedResources(10);
```

Bind that application policy to the configured runtime:

```java
JsonMapper mapper = JsonMapper.builder()
    .build();
JsonApi selectedApi = JsonApiJackson3.builder(mapper)
    .representationPolicy(policy)
    .build();
```

Use the decoded selection for this write, with the author's data already available:

```java
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

Result, formatted for display:

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

Read the primary DTO and included core resources together:

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

`primaryArticle.author()` still contains only the `people` / `p1` identifier. `included` contains
the separate person resource with Ada's name; it is not injected into the DTO's relationship.

The runtime defaults deny include traversal, allow selected sparse fields, and enforce traversal
bounds. Selections use wire names, and policy allowances name the owning resource type. Nested paths
need permission at each segment. Inclusion does not cause persistence fetching: supply already
available objects or implement application lookup before writing.

!!! warning "Selection is not permission"
    An include request cannot override application representation policy. Policy is not a substitute
    for endpoint/resource/field authorization. Do not copy an unrestricted `allowAll()` policy into a
    public endpoint merely to make a query work.

Sparse fieldsets shape output per resource type. Inclusion and linkage remain separate: requesting
`included` can retain resources even when a fieldset omits their linking relationship.

## What stays with the application

| Parameter | Library result | Application work |
|-----------|----------------|------------------|
| `include`, `fields[TYPE]` | Representation selection | Authorize, load permitted data, apply runtime policy |
| `sort` | Ordered field/direction values | Map permitted names to actual query ordering |
| `page`, `filter`, unknown parameters | Ordered opaque value lists | Define semantics, validate values, execute queries |

Parsing preserves repeated opaque values and encounter order. It does not calculate page links,
interpret filters, run sorting, or choose an HTTP status. Query failures use `JsonApiQueryException`
with `diagnostic()` and an optional `parameterName()`, separate from document/mapping failures.

See [`JsonApiQueryParser`](https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-query/src/main/java/com/kazforge/jsonapi/query/JsonApiQueryParser.java)
and [`RepresentationPolicy`](https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-api/src/main/java/com/kazforge/jsonapi/representation/RepresentationPolicy.java)
for exact parsing and policy contracts.
