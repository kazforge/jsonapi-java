---
title: "Diagnose JSON:API validation, mapping, and query errors"
description: "Diagnose JSON:API document validation, Java DTO mapping, and query parsing failures using stable codes and locations, with application-owned error responses."
---

# Diagnose JSON:API validation, mapping, and query errors {#diagnostics}

Use structured diagnostics to distinguish **what failed** from **where it failed**. The library
reports failures; your application decides whether they describe a client error, how to respond,
and what is safe to expose or log. Exception messages are explanatory text, not stable codes or
automatically safe public detail.

## What failed?

| Exception family | Stage | Machine-readable properties |
|------------------|-------|-----------------------------|
| [`JsonApiDocumentReadException`][read-exception] | JSON decoding, local core construction, or aggregate validation during a document read | `category()`, document-relative `jsonPointer()`, non-null `sourceLocation()`, nullable `ruleCode()` |
| [`JsonApiMappingException`][mapping-exception] | Resource mapping, DTO binding, registries, decoration/representation, or PATCH projection | `diagnostic()`, nullable `resourceClass()`, nullable `location()`; `propertyPath()` is the location's string view |
| [`JsonApiValidationException`][validation-exception] | Direct core construction, explicit aggregate validation, or a validated write | `ruleCode()`, `jsonPointer()` |
| [`JsonApiQueryException`][query-exception] | Raw/decoded query parsing or optional query allow-list checks | `diagnostic()`, nullable decoded `parameterName()` |

A successfully decoded and validated core document can still fail DTO binding. That later failure
is a mapping exception, not a document-read exception. Similarly, a representation-policy rejection
while mapping is a mapping failure, not a query-parsing failure. None of these families alone tells
you whether a failure originated in an incoming request, application configuration, or output data.

For document reads, `category()` distinguishes codec failures such as malformed JSON, unexpected
tokens, and duplicate members from validation failures. `ruleCode()` carries a core validation rule
for local or aggregate validation; codec failures do not invent a rule code.

Branch on the stable [`CodecFailureCategory`][codec-category],
[`ValidationRuleCode`][validation-code], [`MappingDiagnostic`][mapping-code], or
[`QueryDiagnostic`][query-code], not message text. Their source Javadoc owns the exhaustive code
inventory and contracts. An application may translate selected values into its own public error
taxonomy; the library does not choose JSON:API error codes or HTTP status.

## Where did it fail?

### Document and construction paths

Document-read `jsonPointer()` values are document-relative RFC 6901 JSON Pointers. For example,
`/data/relationships/author/data/type` addresses the author's linkage type, and
`/included/0/type` addresses the first included resource's type. The empty string `""` denotes the
document root. A malformed/truncated document may instead report an enclosing path such as `/data`;
a diagnostic coordinate is not a guarantee that a complete value exists there.

Core validation paths depend on the operation. Aggregate validation addresses the core document,
but a constructor checks its own construction context. For example, an invalid `ErrorSource` pointer
is reported at `/errors/source/pointer`; this does not establish a coordinate in an incoming request.
Validated-write failures describe the document being written, not necessarily request data.

### Mapping coordinates and wire names

[`MappingLocation`][mapping-location] is the canonical structural mapping coordinate:
`exception.location().pointer()` returns its text when `location()` is non-null.
`exception.propertyPath()` returns the same text, or null when the location is absent.

Direct resource mapping and binding use **resource-relative** pointers, for example
`/attributes/headline` or `/relationships/author/data`. Configured Jackson external names appear
in the path: a Java property named `title` that is renamed to `headline` is reported as
`/attributes/headline`, not `/attributes/title`.

Advanced typed-envelope readers compose document prefixes with those resource-local paths:

| Resource occurrence | Example composed location |
|---------------------|---------------------------|
| Single primary resource | `/data/attributes/headline` |
| First resource in a primary collection | `/data/0/relationships/author/data` |
| Second included resource | `/included/1/attributes/title` |

An envelope failure can identify the enclosing resource, such as `/included/1`, even when no local
member path is available. Do not infer the coordinate basis from the exception family or the fact
that JSON was read: an ordinary Level-1 resource read can still expose a resource-relative mapping
pointer. Use the contract of the operation you invoked.

When no meaningful member coordinate applies, the mapping location is **null**, not `""`, `/`, or
a class name. `resourceClass()` is optional context, not a request pointer. In RFC 6901, `/` refers
to a member with an empty name; it is not the document root or a placeholder for an absent location.

Escape each raw segment independently: `~` becomes `~0`, then `/` becomes `~1`. Do not escape the
whole pointer as one string or concatenate unescaped names. For a nested JSON object key `a/b~c`
inside an attribute named `details`:

```java
import com.kazforge.jsonapi.diagnostic.MappingLocation;

MappingLocation nested = MappingLocation.of("attributes", "details", "a/b~c");
// /attributes/details/a~1b~0c
```

Rebase a resource-relative location only when the application knows its occurrence in the request.
For a resource known to be the third entry of request `data`, structural composition is
`MappingLocation.of("data", "2").append(relativeLocation)`. This produces a document-relative path;
it does not prove that the target exists or that the failure belongs in a client response.

### Query parameters

Query `parameterName()` is the **decoded** name, for example `fields[articles]`, even when the raw
query contains `fields%5Barticles%5D`. Use it as a candidate for `source.parameter`, not as a JSON
Pointer or a Java property name. Malformed encoding of the name can prevent attribution, leaving
`parameterName()` null; do not invent a parameter name. See
[query parsing and policy](query-and-representation.md) for the separate responsibilities of parsing,
allow-list checks, and representation policy.

### Numeric source positions

Document-read `sourceLocation()` is always non-null. [`SourceLocation`][source-location] contains
only `lineNumber()`, `columnNumber()`, `charOffset()`, and `byteOffset()`, never source text.
A negative component is unavailable. `SourceLocation.UNKNOWN` has no available components, and
`isKnown()` means **at least one** is available, not that all four are usable.

Adapters copy best-effort backend token/cursor positions. Availability and precision vary with
Jackson major, input form, and failure; a byte offset need not accompany a character offset, and
the reported position need not be the exact offending character. Treat positions as diagnostic
context, not another spelling of a JSON Pointer. Line, column, and offsets are not standard JSON:API
error-source members.

The numeric value is payload-safe; that does **not** make exception messages, causes, resource
class names, or other diagnostic context safe for public responses or unrestricted logging.

## Build an application-owned error response

The [JSON:API error-object specification][error-spec] requires `source.pointer` to identify an
**existing value in the request document**. Diagnostic paths may instead name a missing member,
an enclosing structure, application-created values, or output-side data. [`ErrorSource`][error-source]
checks pointer syntax only; it does not resolve a pointer against a request.

Before adding attribution, establish the failure's request-side origin, its coordinate basis, and
the existing request value or parameter that caused it. Do not publish a missing-member pointer
verbatim. An existing enclosing value may be appropriate when it is the source of the error;
otherwise omit attribution. For malformed JSON, a diagnostic pointer alone does not establish an
existing request value.

This deliberately selective example represents **one application's policy**, not a general
exception converter. Call it only for failures from that application's request decoding/query
parsing. The application supplies `verifiedRequestPointer` only after establishing that it names
an existing value in the same request and is appropriate error attribution; use `Optional.empty()`
otherwise. For example, a request with `"attributes": "not-an-object"` has an existing value at
`/data/attributes` that may be approved for an unexpected-token response.

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
            .code("invalid-request-value")
            .title("A request value has the wrong JSON shape")
            .source(source)
            .build());
    }
    if (failure instanceof JsonApiQueryException query
            && query.diagnostic() == QueryDiagnostic.INVALID_FIELDSET_SYNTAX) {
        String parameter = query.parameterName();
        return Optional.of(ErrorObject.builder()
            .code("invalid-field-selection")
            .title("The field selection is invalid")
            .source(parameter == null ? null : ErrorSource.ofParameter(parameter))
            .build());
    }
    return Optional.empty(); // Not handled by this policy; not a successful request.
}
```

The example uses stable diagnostics to select application-chosen codes and fixed public titles,
never `getMessage()` or cause text. It omits unavailable query attribution and leaves unapproved
failures to another application path. Other approved errors can be built without `source`; do not
manufacture one merely to fill the response.

Mapping, direct construction, and write failures are not automatically client errors. Application
policy still owns HTTP status, safe detail, response emission, logging/redaction, and security.
[`ErrorObject`][error-object] is only a core value. To wrap and serialize selected errors, use the
existing [error-document example](relationships-and-documents.md#error-documents).

[read-exception]: https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-api/src/main/java/com/kazforge/jsonapi/diagnostic/JsonApiDocumentReadException.java
[mapping-exception]: https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-api/src/main/java/com/kazforge/jsonapi/diagnostic/JsonApiMappingException.java
[validation-exception]: https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-core/src/main/java/com/kazforge/jsonapi/core/validation/JsonApiValidationException.java
[query-exception]: https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-query/src/main/java/com/kazforge/jsonapi/query/JsonApiQueryException.java
[codec-category]: https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-api/src/main/java/com/kazforge/jsonapi/diagnostic/CodecFailureCategory.java
[validation-code]: https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-core/src/main/java/com/kazforge/jsonapi/core/validation/ValidationRuleCode.java
[mapping-code]: https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-api/src/main/java/com/kazforge/jsonapi/diagnostic/MappingDiagnostic.java
[query-code]: https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-query/src/main/java/com/kazforge/jsonapi/query/QueryDiagnostic.java
[mapping-location]: https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-api/src/main/java/com/kazforge/jsonapi/diagnostic/MappingLocation.java
[source-location]: https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-api/src/main/java/com/kazforge/jsonapi/diagnostic/SourceLocation.java
[error-source]: https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-core/src/main/java/com/kazforge/jsonapi/core/model/ErrorSource.java
[error-object]: https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-core/src/main/java/com/kazforge/jsonapi/core/model/ErrorObject.java
[error-spec]: https://jsonapi.org/format/1.1/#error-objects
