# Relationships and documents

Use the configured `JsonApi api` from [getting started](getting-started.md#configure-a-runtime).
Operation snippets belong in a method; imports are shown with each example.

## Relationship linkage documents

The relationship facet handles identifiers as primary `data`. It does not return a related-resource
DTO or fetch a person:

```java
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import java.util.List;

ResourceIdentifier author = ResourceIdentifier.of("people", "p1");
String toOne = api.relationships().writeToOne(author);
assert api.relationships().readToOne(toOne).equals(author);

String cleared = api.relationships().writeToOne(null);
assert api.relationships().readToOne(cleared) == null;

String toMany = api.relationships().writeToMany(List.of(author));
assert api.relationships().readToMany(toMany).equals(List.of(author));

String empty = api.relationships().writeToMany(List.of());
assert api.relationships().readToMany(empty).isEmpty();
```

`cleared` contains `{"data":null}`; `empty` contains `{"data":[]}`. To-one and to-many reads reject
the other cardinality rather than converting it. Top-level links/meta on a linkage document require
the explicit document or advanced codec path.

## Preserve document state

Raw reads require a semantic context because a type/id-only object or empty array does not tell the
reader whether you mean resources or identifiers.

```java
import com.kazforge.jsonapi.core.model.DocumentData;
import com.kazforge.jsonapi.core.model.JsonApiDocument;
import com.kazforge.jsonapi.document.DocumentReadContext;

JsonApiDocument document = api.documents().read(
    "{\"data\":null}", DocumentReadContext.resourceDefaults());
assert document.data() instanceof DocumentData.NullData;
String preserved = api.documents().write(document);
assert preserved.equals("{\"data\":null}");
```

In the core model, a containing Java null means an absent member. Explicit-null data/linkage uses
a sealed variant; an empty collection or wrapper stays present-empty. A flat DTO cannot represent
every one of these states.

For relationship-endpoint reads, identifier decoding and endpoint validation are separate settings:

```java
import com.kazforge.jsonapi.core.aggregate.ValidationContext;
import com.kazforge.jsonapi.core.validation.PrimaryDataContext;
import com.kazforge.jsonapi.document.PrimaryDataKind;

DocumentReadContext linkageContext = DocumentReadContext.of(
    ValidationContext.defaults().withPrimaryDataContext(PrimaryDataContext.RELATIONSHIP),
    PrimaryDataKind.RESOURCE_IDENTIFIER);
JsonApiDocument linkage = api.documents().read(
    "{\"data\":{\"type\":\"people\",\"id\":\"p1\"}}", linkageContext);
assert linkage.data() instanceof DocumentData.SingleIdentifier;
```

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

Relationship authorLink = Relationship.linkOnly(Links.ofLinks(Map.of(
    "related", new Link.StringLink("/articles/1/author"))));
Relationship authorMeta = Relationship.metaOnly(Meta.of(Map.of("visible", false)));
assert !authorLink.hasDataMember();
assert !authorMeta.hasDataMember();

ResourceObject resource = new ResourceObject("articles", "1", null, null,
    Relationships.ofRelationships(Map.of("author", authorLink)), null, null, Map.of());
String linksOnly = api.documents().write(
    JsonApiDocument.withData(new DocumentData.SingleResource(resource)));
assert !linksOnly.contains("\"author\":{\"data\"");
```

Core constructors check local invariants; document writers also validate whole-document rules.
With only `jsonapi-java-core` and no adapter, run aggregate validation explicitly on the same model:

```java
import com.kazforge.jsonapi.core.aggregate.JsonApiDocumentValidator;

new JsonApiDocumentValidator().validate(
    JsonApiDocument.withData(new DocumentData.SingleResource(resource)), ValidationContext.defaults());
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

ErrorObject error = ErrorObject.builder()
    .status("422")
    .code("title-required")
    .title("Title is required")
    .source(ErrorSource.builder().pointer("/data/attributes/title").build())
    .build();
String errors = api.documents().write(JsonApiDocument.withError(error));
JsonApiDocument readErrors = api.documents().read(errors, DocumentReadContext.resourceDefaults());
assert readErrors.errors().equals(java.util.List.of(error));
assert !readErrors.hasDataMember();
```

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
