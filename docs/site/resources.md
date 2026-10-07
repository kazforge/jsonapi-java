# Resources

Use the configured `JsonApi api` from [getting started](getting-started.md#configure-a-runtime).

## Write relationships, read linkage

An article can carry an author object when you write it. A flat read returns the author's
identifier—not the author loaded from `included`.

```java
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
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

`author` identifies resource type `people`, id `p1`. It does not contain Ada's name.

Built-in read targets include `ResourceIdentifier` and its optional/collection/array forms. Custom
relationship targets need an explicit linkage mapper; mapping does not look up persisted people.
Use [selection and policy](query-and-representation.md) to include author resources on writes.

!!! warning "Mapped relationships carry data"
    Every selected ordinary mapped relationship has `data`. A null to-one value becomes
    `"data": null`; an empty to-many value becomes `"data": []`. An empty optional is not a
    links-only relationship. Use the [document path](relationships-and-documents.md#links-only-and-meta-only-relationships)
    when relationship `data` must be absent.

## Collections and shape

Using the `article` above:

```java
import java.util.List;

List<ArticleWithAuthor> articles = List.of(article);
String collectionJson = api.resources().writeMany(articles);
```

Read the resulting resource array with the collection operation:

```java
List<ArticleView> articleViews = api.resources().readMany(
    collectionJson,
    ArticleView.class
);
```

The list contains one `ArticleView`, with id `1` and author linkage to `people` / `p1`.

`readOne` requires one resource object; `readMany` requires a resource array, including `[]`.
Neither coerces null, absent `data`, linkage documents, or errors into a DTO or an empty list.
For those states, use [documents](relationships-and-documents.md#preserve-document-state).

## Create, id, and lid

Create authoring allows a primary resource without `id`. A local identifier is an independent,
document-scoped `lid`; it is not a temporary value secretly substituted for `id`.

```java
import com.kazforge.jsonapi.annotation.JsonApiLocalId;

@JsonApiResource(type = "articles")
public record NewArticle(
    @JsonApiId String id,
    @JsonApiLocalId String localId,
    @JsonApiAttribute String title
) {}
```

```java
NewArticle draft = new NewArticle(
    null,
    "draft-1",
    "A draft"
);
String createJson = api.resources().writeCreateDocument(draft);
```

Result:

```json
{
  "data": {
    "type": "articles",
    "lid": "draft-1",
    "attributes": {
      "title": "A draft"
    }
  }
}
```

Ordinary response writes still require `id`. Create allowances are not a nested-create protocol:
included resources and unrelated linkage need ids. No annotation infers `lid`, and neither
identifier role falls back to the other.

## Author an update document

An update requires `type` and `id`. Supply an expected endpoint identity when you have one:

```java
import com.kazforge.jsonapi.core.validation.EndpointIdentity;

EndpointIdentity target = new EndpointIdentity("articles", "1");
String updateJson = api.resources().writeUpdateDocument(article, target);
```

The document has the same resource shape as the article write above, including `type: articles`
and `id: 1`. This operation additionally validates it as an update request against `target`.

An identity mismatch fails validation. This writes the selected DTO members; it does not calculate
a diff or turn null DTO fields into omission. Use a [presence-aware PATCH schema](patches.md) to
read partial update requests. Author partial updates with an explicitly chosen schema/selection
or the advanced document writer, not a guessed full-object diff.

## Top-level state

Use an envelope for document-level metadata, links, or an explicit `jsonapi` object:

```java
import com.kazforge.jsonapi.api.ResourceWriteOptions;
import com.kazforge.jsonapi.core.model.Meta;
import com.kazforge.jsonapi.document.DocumentEnvelope;
import java.util.Map;

Meta requestMeta = Meta.of(Map.of("request", "req-7"));
DocumentEnvelope envelope = new DocumentEnvelope(
    null,
    requestMeta,
    null
);
ResourceWriteOptions options = ResourceWriteOptions.defaults()
    .withEnvelope(envelope);

String envelopeJson = api.resources().writeOne(article, options);
```

This adds a top-level `meta` object to the resource document:

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
  "meta": {
    "request": "req-7"
  }
}
```

Read the DTO and document-level state together:

```java
import com.kazforge.jsonapi.api.ResourceDocument;

ResourceDocument<ArticleView> result = api.resources().readOneDocument(
    envelopeJson,
    ArticleView.class
);
ArticleView primaryArticle = result.resource();
Meta documentMeta = result.meta();
```

`primaryArticle` has id `1`; `documentMeta` carries `request: req-7`.

The typed document result carries `included` as validated core resources, not hydrated DTOs.
Resource meta, relationship meta, and identifier meta are different locations; an envelope supplies
only top-level members. Full wire-state reads belong to the [document facet](relationships-and-documents.md).

## Naming and failures

Use JSON:API member names in selections, not Java field names. Configure renaming and conversion
on Jackson before creating the runtime; role annotations do not override Jackson-ignored properties.
DTO mapping is not a lossless representation of member presence.

Reads validate the whole document before binding. Malformed/invalid input and DTO mapping problems
remain [distinct failure families](relationships-and-documents.md#diagnostics).
[`JsonApiResources` Javadoc](https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-api/src/main/java/com/kazforge/jsonapi/api/JsonApiResources.java)
owns generic-type and stream overload contracts; caller-supplied streams remain open.
