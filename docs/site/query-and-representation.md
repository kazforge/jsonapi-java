# Query and representation

Add `com.kazforge:jsonapi-java-query` at the same version as your adapter. This module is independent
of Jackson major and HTTP framework. Use the article/people write model and `ArticleView` from
[resources](resources.md#write-relationships-read-linkage).

## Parse a request

Put this snippet in a method; it parses input, not a persistence query:

```java
import com.kazforge.jsonapi.query.JsonApiQuery;
import com.kazforge.jsonapi.query.JsonApiQueryParser;

JsonApiQuery query = new JsonApiQueryParser().parseRaw(
    "?include=author&fields[articles]=title,author&sort=-title"
        + "&page[number]=2&filter[status]=published");
assert query.selection().includePaths().size() == 1;
assert query.sortFields().size() == 1;
assert query.pageParameters().get("page[number]").equals(java.util.List.of("2"));
```

For framework parameters that are already decoded, use `parseDecoded`; do not URL-decode twice:

```java
import java.util.List;
import java.util.Map;

JsonApiQuery selected = new JsonApiQueryParser().parseDecoded(Map.of(
    "include", List.of("author"), "fields[articles]", List.of("title,author")));
```

`include`, each `fields[TYPE]`, and `sort` require **one value occurrence**, with comma-separated
tokens inside that value. Names are exact JSON:API tokens, not trimmed Java property names. Explicit `include=` or
`fields[articles]=` stays distinct from omission. `QueryAllowList` can reject unpermitted include,
field, or sort tokens, but a successful parse grants no access to a resource or field.

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

RepresentationPolicy policy = RepresentationPolicy.defaults()
    .withIncludePolicy(IncludePolicy.allowing(Set.of(
        RelationshipAllowance.of("articles", "author"))))
    .withMaxIncludeDepth(1)
    .withMaxIncludedResources(10);
JsonApi selectedApi = JsonApiJackson3.builder(JsonMapper.builder().build())
    .representationPolicy(policy).build();

ArticleWithAuthor article = new ArticleWithAuthor("1", "Working with JSON:API",
    new Person("p1", "Ada"));
String json = selectedApi.resources().writeOne(article,
    ResourceWriteOptions.defaults().withSelection(selected.selection()));
var result = selectedApi.resources().readOneDocument(json, ArticleView.class);
assert result.included().size() == 1;
assert result.included().getFirst().type().equals("people");
assert result.resource().author().id().equals("p1");
```

The runtime defaults deny include traversal, allow selected sparse fields, and bound traversal.
Selections use wire names, and policy allowances name the owning resource type. Nested paths need
permission at each segment. Inclusion does not cause persistence fetching: supply already available
objects or implement application lookup before writing.

!!! warning "Selection is not permission"
    An include request cannot override application representation policy. Policy is not a substitute
    for endpoint/resource/field authorization. Do not copy an unrestricted `allowAll()` policy into a
    public endpoint merely to make a query work.

Sparse fieldsets shape output per resource type. Inclusion and linkage remain separate: requesting
`included` can retain resources even when a fieldset omits their linking relationship. Mapped writes
carry the necessary validation provenance internally; do not unwrap a `MappedDocument` and discard
that provenance when composing advanced writes.

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
