# Relationships and documents

Use the configured `JsonApi api` from [getting started](getting-started.md#configure-a-runtime).

## Relationship linkage documents

The relationship facet handles identifiers as primary `data`. It does not return a related-resource
DTO or fetch a person. Write a to-one linkage document for an existing author:

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

Use null to clear to-one linkage:

```java
String clearedAuthorJson = api.relationships().writeToOne(null);
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
cardinality rather than converting it. Top-level links/meta on a linkage document require the
explicit document or advanced codec path.

## Preserve document state

Raw reads require a semantic context because a type/id-only object or empty array does not tell the
reader whether you mean resources or identifiers. For a response body (`responseBody`) containing
explicit-null primary data:

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

In the core model, a containing Java null means an absent member. Explicit-null data/linkage uses
a sealed variant; an empty collection or wrapper stays present-empty. A flat DTO cannot represent
every one of these states.

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

`identifierDefaults()` alone does **not** select relationship-endpoint validation. The document
facet writes with response/other validation; raw create/update writing with a custom validation
context is advanced. Ordinary create/update authoring uses [resources](resources.md).

## Links-only and meta-only relationships

Ordinary mapped relationships always carry `data`. For a response that intentionally leaves it out,
construct the relationship in the core model:

```java
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

ResourceObject articleResource = new ResourceObject(
    "articles",
    "1",
    null,           // lid
    null,           // attributes
    relationships,
    null,           // resource links
    null,           // resource meta
    Map.of()
);
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

Read error documents through the document facet, not a resource DTO operation:

```java
JsonApiDocument readErrors = api.documents().read(errorJson, resourceContext);
List<ErrorObject> reportedErrors = readErrors.errors();
```

`reportedErrors` contains the title-required error; the document has no `data` member.

`source.pointer` is syntax-checked; the library does not resolve it against the request payload.
It does not convert every library exception into an HTTP error automatically.

## Diagnostics

| Failure | Inspect | Meaning |
|---------|---------|---------|
| `JsonApiDocumentReadException` | `category()`, `jsonPointer()`, `sourceLocation()`, `ruleCode()` | Decode or read-time validation failed |
| `JsonApiMappingException` | `diagnostic()`, `location()` | Mapping, binding, representation, or PATCH projection failed |
| `JsonApiValidationException` | `ruleCode()`, `jsonPointer()` | Direct core construction/validation or a validated write failed |

Keep these families separate. A successfully decoded document can still fail DTO binding. Locations
may be absent when no member coordinate is meaningful; do not expose payloads or internal exception
messages as client-facing detail by default. See the
[diagnostic API Javadoc](https://github.com/kazforge/jsonapi-java/tree/main/jsonapi-java-api/src/main/java/com/kazforge/jsonapi/diagnostic)
and [validation API Javadoc](https://github.com/kazforge/jsonapi-java/tree/main/jsonapi-java-core/src/main/java/com/kazforge/jsonapi/core/validation).

## Ordinary or Advanced?

| Need | Use |
|------|-----|
| Homogeneous resource DTOs, linkage, raw documents, typed PATCH | Neutral `JsonApi` facets |
| Exact absent/null/empty state, links-only relationships, errors | `api.documents()` with core values and explicit read context |
| Native parameterized Jackson `JavaType`, custom codec validation, per-call mapping policy | Major-specific readers, writers, mappers, binders |
| Heterogeneous typed primary/included resources | Advanced domain-document reader with explicit `ResourceTypeRegistry` |

The advanced factories are on
[`JsonApiJackson3`](https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-jackson3/src/main/java/com/kazforge/jsonapi/jackson3/JsonApiJackson3.java)
and [`JsonApiJackson2`](https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-jackson2/src/main/java/com/kazforge/jsonapi/jackson2/JsonApiJackson2.java).
Advanced typed envelopes bind included resources independently, not into a relationship graph.
Jackson 2 advanced I/O uses checked `IOException`; its Level-1 stream failures use
`UncheckedIOException`. Jackson 3 follows its native unchecked exception model. Caller-owned sources
and sinks remain open.
