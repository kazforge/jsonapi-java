# Resources

Use the configured `JsonApi api` from [getting started](getting-started.md#configure-a-runtime).
The following declarations can be nested public records in your example class; operation snippets
go in a method using that runtime. Each snippet lists its additional imports.

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
public record Person(@JsonApiId String id, @JsonApiAttribute String name) {}

@JsonApiResource(type = "articles")
public record ArticleWithAuthor(
    @JsonApiId String id,
    @JsonApiAttribute String title,
    @JsonApiRelationship Person author) {}

@JsonApiResource(type = "articles")
public record ArticleView(
    @JsonApiId String id,
    @JsonApiAttribute String title,
    @JsonApiRelationship ResourceIdentifier author) {}
```

```java
ArticleWithAuthor article = new ArticleWithAuthor("1", "Working with JSON:API",
    new Person("p1", "Ada"));
String one = api.resources().writeOne(article);
ArticleView view = api.resources().readOne(one, ArticleView.class);
assert view.author().equals(ResourceIdentifier.of("people", "p1"));
```

The default write emits linkage, but no `included` author:

```json
{"data":{"type":"articles","id":"1","attributes":{"title":"Working with JSON:API"},"relationships":{"author":{"data":{"type":"people","id":"p1"}}}}}
```

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

String many = api.resources().writeMany(List.of(article));
List<ArticleView> views = api.resources().readMany(many, ArticleView.class);
assert views.size() == 1;
assert views.getFirst().id().equals("1");
```

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
    @JsonApiAttribute String title) {}
```

```java
String create = api.resources().writeCreateDocument(
    new NewArticle(null, "draft-1", "A draft"));
assert create.contains("\"lid\":\"draft-1\"");
assert !create.contains("\"id\"");
```

Ordinary response writes still require `id`. Create allowances are not a nested-create protocol:
included resources and unrelated linkage need ids. No annotation infers `lid`, and neither
identifier role falls back to the other.

## Author an update document

An update requires `type` and `id`. Supply an expected endpoint identity when you have one:

```java
import com.kazforge.jsonapi.core.validation.EndpointIdentity;

String update = api.resources().writeUpdateDocument(
    article, new EndpointIdentity("articles", "1"));
assert update.contains("\"id\":\"1\"");
```

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

Meta meta = Meta.of(Map.of("request", "req-7"));
ResourceWriteOptions options = ResourceWriteOptions.defaults()
    .withEnvelope(new DocumentEnvelope(null, meta, null));
String enveloped = api.resources().writeOne(article, options);
var result = api.resources().readOneDocument(enveloped, ArticleView.class);
assert result.meta().equals(meta);
assert result.resource().author().id().equals("p1");
```

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
