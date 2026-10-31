---
title: "Diagnose JSON:API validation, mapping, and query errors"
description: "Diagnose JSON:API document validation, Java DTO mapping, and query parsing failures using stable codes and locations, with application-owned error responses."
---

# Diagnose JSON:API validation, mapping, and query errors {#diagnostics}

When a JSON:API operation fails, the library throws an exception with machine-readable diagnostic
codes and location information so your application can distinguish **what failed** from **where it
failed**:

**Invalid request → library exception → stable diagnostic information → application decision → JSON:API error response.**

Reuse `JsonApi api` and the `Article` record from
[getting started](getting-started.md#read-and-write-an-article). Suppose a client sends this create
request (`invalidArticleBody`), mistakenly supplying `"attributes"` as a string instead of a JSON
object:

```json
{
  "data": {
    "type": "articles",
    "attributes": "Working with JSON:API"
  }
}
```

Reading the request with `readCreate` throws [`JsonApiDocumentReadException`][read-exception]
before DTO binding begins:

```java
import com.kazforge.jsonapi.diagnostic.CodecFailureCategory;
import com.kazforge.jsonapi.diagnostic.JsonApiDocumentReadException;

try {
    api.resources().readCreate(invalidArticleBody, Article.class);
} catch (JsonApiDocumentReadException read) {
    CodecFailureCategory category = read.category(); // CodecFailureCategory.UNEXPECTED_TOKEN
    String pointer = read.jsonPointer();             // "/data/attributes"
}
```

Instead of exposing `read.getMessage()`, your application checks the stable `category`
(`UNEXPECTED_TOKEN`), verifies that `"/data/attributes"` identifies an existing value in
`invalidArticleBody`, and emits its own JSON:API error response (see
[Build an application-owned error response](#build-an-application-owned-error-response)):

```json
{
  "errors": [
    {
      "status": "400",
      "code": "invalid-request-value",
      "title": "A request value has the wrong JSON shape",
      "source": {
        "pointer": "/data/attributes"
      }
    }
  ]
}
```

The library reports failures; your application decides whether they describe a client error, how to
respond, and what is safe to expose or log. Exception messages are explanatory text for developers,
not stable codes or automatically safe public error details.

## What failed?

| Exception family | Stage | Machine-readable properties |
|------------------|-------|-----------------------------|
| [`JsonApiDocumentReadException`][read-exception] | JSON decoding, local core construction, or aggregate validation during a document read | `category()`, document-relative `jsonPointer()`, non-null `sourceLocation()`, nullable `ruleCode()` |
| [`JsonApiMappingException`][mapping-exception] | Resource mapping, DTO binding, registries, link decoration, include/fieldset selection, or PATCH binding | `diagnostic()`, nullable `resourceClass()`, nullable `location()`; `propertyPath()` is the location's string view |
| [`JsonApiValidationException`][validation-exception] | Direct core construction, explicit aggregate validation, or a validated write | `ruleCode()`, `jsonPointer()` |
| [`JsonApiQueryException`][query-exception] | Raw/decoded query parsing or optional query allow-list checks | `diagnostic()`, nullable decoded `parameterName()` |

Each exception family corresponds to a distinct stage:

- **`JsonApiDocumentReadException`:** For document reads, `category()` separates codec failures
  (`MALFORMED_JSON`, `UNEXPECTED_TOKEN`, `DUPLICATE_MEMBER`, where `ruleCode()` is `null`) from
  JSON:API validation failures (`LOCAL_VALIDATION`, `AGGREGATE_VALIDATION`, where `ruleCode()`
  carries a [`ValidationRuleCode`][validation-code]). For example, passing the ID-less
  [`createBody` request from Resources](resources.md#read-an-incoming-create-request) to
  `api.resources().readOne(createBody, Article.class)` fails with `category()` equal to
  `AGGREGATE_VALIDATION`, `ruleCode()` equal to `ValidationRuleCode.RESOURCE_ID_REQUIRED`, and
  `jsonPointer()` equal to `"/data/id"`.
- **`JsonApiMappingException`:** A successfully decoded and validated core document can still fail
  DTO binding or mapping. Reading a valid `"people"` document
  (`{"data": {"type": "people", "id": "p1"}}`) into `Article.class` with `api.resources().readOne`
  fails with `diagnostic()` equal to `MappingDiagnostic.RESOURCE_TYPE_MISMATCH`, `resourceClass()`
  equal to `Article.class`, and `propertyPath()` equal to `"/type"`. Rejection by representation
  policy during `writeOne` also produces a `JsonApiMappingException`
  (`DENIED_RELATIONSHIP_INCLUDE` or `DENIED_FIELDSET_FIELD`), rather than a query-parsing failure.
- **`JsonApiValidationException`:** Thrown when constructing core model objects directly (such as
  `ErrorSource.builder().pointer("invalid").build()` failing with
  `ValidationRuleCode.INVALID_JSON_POINTER`), running `JsonApiDocumentValidator` explicitly, or
  writing an invalid document.
- **`JsonApiQueryException`:** Thrown by `JsonApiQueryParser` when a raw or decoded query string
  has malformed encoding, invalid parameter syntax, or an unpermitted allow-list token (for
  example, `new JsonApiQueryParser().parseRaw("?fields[articles]=title,")` failing with
  `QueryDiagnostic.INVALID_FIELDSET_SYNTAX` and `parameterName()` equal to `"fields[articles]"`).

Branch on the stable [`CodecFailureCategory`][codec-category],
[`ValidationRuleCode`][validation-code], [`MappingDiagnostic`][mapping-code], or
[`QueryDiagnostic`][query-code], never on `getMessage()` text. Their source Javadoc owns the
exhaustive code inventory and contracts. Before deciding how to respond, establish whether the
failure came from an incoming client request, application configuration, or output data.

## Where did it fail?

### Document and construction paths

Document-read `jsonPointer()` values are document-relative RFC 6901 JSON Pointers:
`"/data/attributes"` in `invalidArticleBody` above, `"/data/relationships/author/data/type"` for
an author's linkage type, `"/included/0/type"` for the first included resource's type, and `""`
for the document root.

A reported `jsonPointer()` does not guarantee that an existing value sits at that path in the
incoming request:

- A validation rule for a missing member reports where the member was expected (such as
  `"/data/id"` for `RESOURCE_ID_REQUIRED`), even though `"id"` is absent from the request.
- A malformed or truncated document may report an enclosing path such as `"/data"` or `""` where no
  complete value exists.
- Direct core constructors and validated writes report paths in the object being constructed or
  written (for example, an invalid `ErrorSource` pointer reports `"/errors/source/pointer"`), not
  locations in an incoming request.

### Mapping paths and JSON member names {#mapping-coordinates-and-wire-names}

[`MappingLocation`][mapping-location] represents a mapping failure's path as JSON Pointer segments:
`exception.location().pointer()` returns its text when `location()` is non-null, and
`exception.propertyPath()` returns the same text (or `null` when absent).

Whether a mapping pointer is **resource-relative** or **document-relative** depends on the invoked
operation:

- Direct resource and PATCH operations (`api.resources().readOne`, `readCreate`, `readMany`,
  `api.patches().readPatch`) bind individual `ResourceObject` values and report
  **resource-relative** pointers such as `"/type"`, `"/attributes/headline"`, or
  `"/relationships/author/data"`—even though `readOne` reads a JSON document.
- Jackson-specific typed document readers (`JsonApiDomainDocumentReader`, which binds primary and
  included resources through a `ResourceTypeRegistry`) prepend the resource's document path:

| Resource occurrence | Resource-relative location | Composed document location |
|---------------------|----------------------------|----------------------------|
| Single primary resource | `/attributes/headline` | `/data/attributes/headline` |
| First resource in a primary collection | `/relationships/author/data` | `/data/0/relationships/author/data` |
| Second included resource | `/attributes/title` | `/included/1/attributes/title` |

A typed document failure can also identify the enclosing resource, such as `"/included/1"`, when no
local member path applies. Configured Jackson external names appear in the pointer: a Java property
`title` renamed to `headline` is reported as `"/attributes/headline"`, not `"/attributes/title"`.

When no meaningful member path applies (such as a DTO missing `@JsonApiResource`), `location()` and
`propertyPath()` are **`null`**—never `""`, `"/"`, or a class name. `resourceClass()` is optional
context, not a request pointer. In RFC 6901, `"/"` refers to a member with an empty name `""`, not
the document root. Do not invent a pointer when `location()` is `null`.

`MappingLocation.of(...)` escapes each raw segment independently (`~` becomes `~0`, then `/`
becomes `~1`). Never escape the whole pointer as a single string or concatenate unescaped names.
For a nested JSON object key `a/b~c` inside an attribute named `details`:

```java
import com.kazforge.jsonapi.diagnostic.MappingLocation;

MappingLocation nested = MappingLocation.of("attributes", "details", "a/b~c");
// nested.pointer() is "/attributes/details/a~1b~0c"
```

Rebase a resource-relative `MappingLocation` (`relativeLocation` from `exception.location()`) only
when your application knows which request resource failed. For a single primary resource,
`MappingLocation.of("data").append(relativeLocation)` prepends `"/data"`; for the third entry of
request `data`, structural composition is
`MappingLocation.of("data", "2").append(relativeLocation)`. Rebasing produces a document-relative
path; it does not prove that the target exists in the request or belongs in a client response.

### Query parameters

Query `parameterName()` is the **decoded** name, for example `"fields[articles]"`, even when the
raw query contains `fields%5Barticles%5D=title,`. Use it as a candidate for `source.parameter`,
never as a JSON Pointer or a Java property name. When malformed percent-encoding of the parameter
name prevents decoding (`QueryDiagnostic.MALFORMED_ENCODING`), `parameterName()` is `null`; do not
invent a parameter name. See [query parsing and policy](query-and-representation.md) for the
separate responsibilities of parsing, allow-list checks, and representation policy.

### Numeric source positions

Document-read `sourceLocation()` is always non-null. [`SourceLocation`][source-location] contains
only `lineNumber()`, `columnNumber()`, `charOffset()`, and `byteOffset()`, never source text.
A negative component is unavailable. `SourceLocation.UNKNOWN` has no available components, and
`isKnown()` means **at least one** is available, not that all four are usable.

The adapter copies best-effort parsing positions from Jackson. Availability and precision vary with
input form and failure; a byte offset need not accompany a character offset, and the reported
position need not be the exact offending character. Treat positions as diagnostic context, not
another spelling of a JSON Pointer. Line, column, and offsets are not standard JSON:API
error-source members.

While the numeric `SourceLocation` is payload-safe, exception messages, causes, resource class
names, and other diagnostic context are **not** automatically safe for public responses or
unrestricted logging.

## Build an application-owned error response

The [JSON:API error-object specification][error-spec] requires `source.pointer` to identify an
**existing value in the request document**. Diagnostic paths may instead name a missing member
(such as `"/data/id"`), a resource-relative path (such as `"/type"`), an application-created value,
or output-side data. [`ErrorSource`][error-source] checks RFC 6901 pointer syntax only; it does not
resolve a pointer against a request.

Never blindly copy a diagnostic pointer into `source.pointer`. Before adding `source`, verify that
the failure came from the incoming request, whether its path starts at the document or one
resource, and which existing request value or parameter caused it. For a missing member, an
existing enclosing value (such as `"/data"`) may be appropriate if it caused the error; otherwise
omit `source`. For malformed JSON, a diagnostic pointer alone does not establish an existing
request value.

This deliberately selective helper represents **one application's policy**, not a general exception
converter. Call it only for failures from that application's request decoding or query parsing. The
application supplies `verifiedRequestPointer` only after establishing that it names an existing
value in the same request (such as `"/data/attributes"` in `invalidArticleBody` above); pass
`Optional.empty()` otherwise:

```java
import com.kazforge.jsonapi.core.model.ErrorObject;
import com.kazforge.jsonapi.core.model.ErrorSource;
import com.kazforge.jsonapi.diagnostic.CodecFailureCategory;
import com.kazforge.jsonapi.diagnostic.JsonApiDocumentReadException;
import com.kazforge.jsonapi.query.JsonApiQueryException;
import com.kazforge.jsonapi.query.QueryDiagnostic;
import java.util.Optional;

static Optional<ErrorObject> requestError(
    RuntimeException failure, Optional<String> verifiedRequestPointer) {
    if (failure instanceof JsonApiDocumentReadException read
            && read.category() == CodecFailureCategory.UNEXPECTED_TOKEN
            && verifiedRequestPointer.filter(read.jsonPointer()::equals).isPresent()) {
        ErrorSource source = ErrorSource.builder().pointer(read.jsonPointer()).build();
        return Optional.of(ErrorObject.builder()
            .status("400")
            .code("invalid-request-value")
            .title("A request value has the wrong JSON shape")
            .source(source)
            .build());
    }
    if (failure instanceof JsonApiQueryException query
            && query.diagnostic() == QueryDiagnostic.INVALID_FIELDSET_SYNTAX) {
        String parameter = query.parameterName();
        return Optional.of(ErrorObject.builder()
            .status("400")
            .code("invalid-field-selection")
            .title("The field selection is invalid")
            .source(parameter == null ? null : ErrorSource.ofParameter(parameter))
            .build());
    }
    return Optional.empty(); // Not handled by this policy; not a successful request.
}
```

For the `JsonApiDocumentReadException` thrown by `invalidArticleBody` at the start of this guide,
calling `requestError(read, Optional.of("/data/attributes"))` returns the `400` [`ErrorObject`][error-object]
with `"pointer": "/data/attributes"`. Wrapping that `ErrorObject` with
`JsonApiDocument.withError(...)` and writing it with `api.documents().write(...)`—as shown in the
[error-document example](relationships-and-documents.md#error-documents)—emits the complete JSON:API
error response shown above.

The helper selects application-chosen statuses, codes, and fixed public titles from stable
diagnostics, rather than `getMessage()` or cause text. When `query.parameterName()` is `null`, it
omits `source` rather than inventing a parameter name; other approved errors can also be built
without `source`. Unhandled failures return `Optional.empty()` for your application's fallback path.
Your application owns HTTP statuses, public error codes, safe messages, authorization, and
logging/redaction policy.

<!-- x-release-please-start-version -->
[read-exception]: https://www.javadocs.dev/com.kazforge/jsonapi-java-api/0.4.1/com/kazforge/jsonapi/diagnostic/JsonApiDocumentReadException.html
[mapping-exception]: https://www.javadocs.dev/com.kazforge/jsonapi-java-api/0.4.1/com/kazforge/jsonapi/diagnostic/JsonApiMappingException.html
[validation-exception]: https://www.javadocs.dev/com.kazforge/jsonapi-java-core/0.4.1/com/kazforge/jsonapi/core/validation/JsonApiValidationException.html
[query-exception]: https://www.javadocs.dev/com.kazforge/jsonapi-java-query/0.4.1/com/kazforge/jsonapi/query/JsonApiQueryException.html
[codec-category]: https://www.javadocs.dev/com.kazforge/jsonapi-java-api/0.4.1/com/kazforge/jsonapi/diagnostic/CodecFailureCategory.html
[validation-code]: https://www.javadocs.dev/com.kazforge/jsonapi-java-core/0.4.1/com/kazforge/jsonapi/core/validation/ValidationRuleCode.html
[mapping-code]: https://www.javadocs.dev/com.kazforge/jsonapi-java-api/0.4.1/com/kazforge/jsonapi/diagnostic/MappingDiagnostic.html
[query-code]: https://www.javadocs.dev/com.kazforge/jsonapi-java-query/0.4.1/com/kazforge/jsonapi/query/QueryDiagnostic.html
[mapping-location]: https://www.javadocs.dev/com.kazforge/jsonapi-java-api/0.4.1/com/kazforge/jsonapi/diagnostic/MappingLocation.html
[source-location]: https://www.javadocs.dev/com.kazforge/jsonapi-java-api/0.4.1/com/kazforge/jsonapi/diagnostic/SourceLocation.html
[error-source]: https://www.javadocs.dev/com.kazforge/jsonapi-java-core/0.4.1/com/kazforge/jsonapi/core/model/ErrorSource.html
[error-object]: https://www.javadocs.dev/com.kazforge/jsonapi-java-core/0.4.1/com/kazforge/jsonapi/core/model/ErrorObject.html
<!-- x-release-please-end -->
[error-spec]: https://jsonapi.org/format/1.1/#error-objects
