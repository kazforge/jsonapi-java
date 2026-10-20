---
title: "Read JSON:API PATCH updates with omission and null presence"
description: "Read JSON:API partial updates into Java PATCH schemas. Distinguish omitted members, explicit null, and supplied values without applying changes to entities."
---

# Read JSON:API PATCH updates with omission and null presence {#patch}

A JSON:API `PATCH` request updates an existing resource identified by `type` and `id`, supplying
only the attributes and relationships the client wants to change. Reuse the configured `JsonApi api`
from [runtime setup](getting-started.md#configure-a-runtime). JSON input names such as
`titleUpdateBody` refer to Java strings containing the displayed document.

## Authoring updates versus reading partial updates

`jsonapi-java` separates outbound update authoring from inbound partial-update binding:

- [`api.resources().writeUpdateDocument(...)`](resources.md#author-an-update-document) **authors**
  and validates an outbound update document from a supplied resource DTO.
- `api.patches().readPatch(...)` **reads** and binds an incoming partial update into a PATCH DTO
  while preserving whether each member was omitted, explicitly `null`, or supplied with a value.
- Neither operation calculates entity diffs or automatically mutates persisted objects.
- Your application owns authorization, business validation, concurrency, and persistence.

An ordinary resource DTO such as `Article(String id, String title, String subtitle)` cannot
represent a partial update safely. When a client omits `subtitle`, a normal Jackson read leaves the
Java field `null`—the exact same state produced when a client explicitly sends `"subtitle": null`
to clear the field. Constructing a complete domain DTO from a partial request forces your code to
guess whether `null` means "keep the current value" or "erase it."

## Typed update presence {#typed-update-presence}

To distinguish omission from explicit `null`, declare an application-owned PATCH schema whose
patchable members use `PatchPresence<T>`.

Suppose an existing article `"1"` has a required `title` and an optional, nullable `subtitle`:

```java
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.patch.PatchPresence;

@JsonApiResource(type = "articles")
public record ArticlePatch(
    @JsonApiId String id,
    @JsonApiAttribute PatchPresence<String> title,
    @JsonApiAttribute PatchPresence<String> subtitle
) {}
```

The `@JsonApiId` component stays unwrapped (`String id`) because every JSON:API update request must
identify its target resource by `id` (never `lid`). Each patchable member is wrapped in the sealed
`PatchPresence<T>` interface:

| Input member | Bound `PatchPresence<T>` state | Requested change |
|--------------|--------------------------------|------------------|
| Omitted from JSON | `PatchPresence.Omitted` | Preserve the existing value |
| Explicit JSON `null` | `PatchPresence.Present` with `value() == null` | Clear the value to `null`, if business rules allow |
| Supplied JSON value | `PatchPresence.Present` with converted `value()` | Replace with the supplied value |

If your application models nullable fields with `Optional<T>`, `PatchPresence<Optional<T>>` keeps
both layers distinct: an omitted member binds to `PatchPresence.Omitted`, while an explicit JSON
`null` converts to `PatchPresence.Present(Optional.empty())`.

### Three basic update requests

Assume the persisted article `"1"` currently has:

- `title`: `"Working with JSON:API"` (required by the application)
- `subtitle`: `"A practical introduction"` (nullable)

#### 1. Omitted field and supplied replacement

This request (`titleUpdateBody`) supplies a new `title` and omits `subtitle`:

```json
{
  "data": {
    "type": "articles",
    "id": "1",
    "attributes": {
      "title": "A practical JSON:API guide"
    }
  }
}
```

Bind the request to `ArticlePatch`:

```java
ArticlePatch titlePatch = api.patches().readPatch(
    titleUpdateBody,
    ArticlePatch.class
);
```

Resulting presence state:

```text
titlePatch.id():       "1"
titlePatch.title():    Present("A practical JSON:API guide")
titlePatch.subtitle(): Omitted
```

Intended application behavior: replace `title` with `"A practical JSON:API guide"` and preserve the
existing `subtitle` (`"A practical introduction"`).

#### 2. Explicit `null` on a nullable attribute

This request (`clearSubtitleBody`) omits `title` and explicitly sets `subtitle` to `null`:

```json
{
  "data": {
    "type": "articles",
    "id": "1",
    "attributes": {
      "subtitle": null
    }
  }
}
```

```java
ArticlePatch clearSubtitlePatch = api.patches().readPatch(
    clearSubtitleBody,
    ArticlePatch.class
);
```

Resulting presence state:

```text
clearSubtitlePatch.id():       "1"
clearSubtitlePatch.title():    Omitted
clearSubtitlePatch.subtitle(): Present(null)
```

Intended application behavior: preserve the existing `title` (`"Working with JSON:API"`) and clear
`subtitle` to `null`.

#### 3. Supplied value on a nullable attribute

This request (`replaceSubtitleBody`) omits `title` and supplies a replacement `subtitle`:

```json
{
  "data": {
    "type": "articles",
    "id": "1",
    "attributes": {
      "subtitle": "Second edition"
    }
  }
}
```

```java
ArticlePatch replaceSubtitlePatch = api.patches().readPatch(
    replaceSubtitleBody,
    ArticlePatch.class
);
```

Resulting presence state:

```text
replaceSubtitlePatch.id():       "1"
replaceSubtitlePatch.title():    Omitted
replaceSubtitlePatch.subtitle(): Present("Second edition")
```

Intended application behavior: preserve the existing `title` and replace `subtitle` with
`"Second edition"`.

### Inspect and apply changes in application code

`PatchPresence<T>` reports what the client sent; your application decides what is valid and applies
the update. Pattern matching over `PatchPresence.Omitted` and `PatchPresence.Present` lets you
keep omitted values, accept `null` on nullable fields, and reject `null` on required fields:

```java
String currentTitle = "Working with JSON:API";
String currentSubtitle = "A practical introduction";

String nextTitle = switch (clearSubtitlePatch.title()) {
    case PatchPresence.Omitted<String> ignored -> currentTitle;
    case PatchPresence.Present<String> supplied -> {
        if (supplied.value() == null) {
            throw new IllegalArgumentException("title cannot be null");
        }
        yield supplied.value();
    }
};

String nextSubtitle = switch (clearSubtitlePatch.subtitle()) {
    case PatchPresence.Omitted<String> ignored -> currentSubtitle;
    case PatchPresence.Present<String> supplied -> supplied.value();
};
```

For `clearSubtitlePatch`, `nextTitle` stays `"Working with JSON:API"` and `nextSubtitle` becomes
`null`. If a client sent `"title": null`, `clearSubtitlePatch.title()` would bind to
`PatchPresence.Present(null)` and your application check would reject it.

## Relationships replace linkage {#relationships-replace-linkage}

In a JSON:API resource `PATCH`, relationship members update **linkage**—the `type` and `id`
identifiers connecting resources—rather than editing the attributes of related objects.

Updating linkage does not automatically fetch, validate, or persist related entities, and neither
PATCH path reads `included`. Your application verifies whether target identifiers exist, enforces
authorization, and updates stored associations.

Declare relationship members on a PATCH DTO using `PatchPresence<T>` around `ResourceIdentifier`
(or a collection of `ResourceIdentifier` for to-many relationships):

```java
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.patch.PatchPresence;
import java.util.List;

@JsonApiResource(type = "articles")
public record ArticleRelationshipsPatch(
    @JsonApiId String id,
    @JsonApiRelationship PatchPresence<ResourceIdentifier> author,
    @JsonApiRelationship PatchPresence<List<ResourceIdentifier>> reviewers
) {}
```

Suppose article `"1"` currently links to author `"p1"` and reviewers `["p2", "p3"]`. Every supplied
relationship in an update request must include `data`:

### Replace a to-one relationship and omit another

This request (`replaceAuthorBody`) points `author` to person `"p2"` and omits `reviewers`:

```json
{
  "data": {
    "type": "articles",
    "id": "1",
    "relationships": {
      "author": {
        "data": {
          "type": "people",
          "id": "p2"
        }
      }
    }
  }
}
```

```java
ArticleRelationshipsPatch authorPatch = api.patches().readPatch(
    replaceAuthorBody,
    ArticleRelationshipsPatch.class
);
```

Resulting presence state:

- `authorPatch.author()` is `PatchPresence.Present` containing a `ResourceIdentifier` with `type()` equal to `"people"` and `id()` equal to `"p2"`.
- `authorPatch.reviewers()` is `PatchPresence.Omitted`.

Intended application behavior: update the article's author reference to `"p2"` (after verifying
that person `"p2"` exists) and leave the existing `["p2", "p3"]` reviewer links unchanged.

### Set to-one linkage to `null`

This request (`clearAuthorBody`) sets `author` linkage to `null` and omits `reviewers`:

```json
{
  "data": {
    "type": "articles",
    "id": "1",
    "relationships": {
      "author": {
        "data": null
      }
    }
  }
}
```

```java
ArticleRelationshipsPatch clearAuthorPatch = api.patches().readPatch(
    clearAuthorBody,
    ArticleRelationshipsPatch.class
);
```

Resulting presence state:

- `clearAuthorPatch.author()` is `PatchPresence.Present` with `value() == null`.
- `clearAuthorPatch.reviewers()` is `PatchPresence.Omitted`.

Intended application behavior: remove the association between article `"1"` and its author (if
business rules allow an unattributed article) without deleting the `people` record itself, and
preserve the existing reviewers.

### Replace to-many linkage with an empty array

This request (`clearReviewersBody`) omits `author` and supplies an empty array for `reviewers`:

```json
{
  "data": {
    "type": "articles",
    "id": "1",
    "relationships": {
      "reviewers": {
        "data": []
      }
    }
  }
}
```

```java
ArticleRelationshipsPatch clearReviewersPatch = api.patches().readPatch(
    clearReviewersBody,
    ArticleRelationshipsPatch.class
);
```

Resulting presence state:

- `clearReviewersPatch.author()` is `PatchPresence.Omitted`.
- `clearReviewersPatch.reviewers()` is `PatchPresence.Present` with an empty list `List.of()` (whereas supplying identifiers produces a list of `ResourceIdentifier` elements carrying each target's `type()` and `id()`).

Intended application behavior: keep author `"p1"` and clear all reviewer links so the article has
no reviewers.

An empty array `"data": []` is a present replacement that clears a to-many relationship; it is
never omission. Likewise, supplying a non-empty identifier array replaces the entire to-many
linkage list rather than appending or removing individual elements. Any per-identifier `meta`
participates in that whole-linkage replacement. To leave a relationship untouched, omit that
relationship member (or omit `"relationships"` entirely).

## Structured attributes and containers {#structured-attributes-and-containers}

When an attribute contains a nested JSON object or array, distinguish three rules:

1. **JSON:API attribute-level semantics:** The JSON:API specification defines partial updates at
   the top-level attribute boundary. If you wrap a regular Java record in `PatchPresence<Details>`,
   Jackson deserializes a supplied `"details"` object as a whole `Details` value, setting any
   omitted nested properties to `null`.
2. **Opt-in recursive typed presence:** When every property of a nested Java shape is also wrapped
   in `PatchPresence<T>`, `jsonapi-java` preserves omission and explicit `null` for each nested
   member. This is an opt-in library mapping feature for structured attributes (and resource-side
   `meta`), not JSON Merge Patch.
3. **Atomic replacement of containers:** `List`, `Set`, arrays, and `Map` values are always atomic
   whole-value replacements. There is no element-addressed array or map patching.

Declare a nested `DetailsPatch` record alongside an atomic `List<String>` attribute. Each public
record below is a separate Java type, normally declared in its own file:

```java
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.patch.PatchPresence;
import java.util.List;

public record DetailsPatch(
    PatchPresence<String> summary,
    PatchPresence<String> language
) {}

@JsonApiResource(type = "articles")
public record StructuredArticlePatch(
    @JsonApiId String id,
    @JsonApiAttribute PatchPresence<DetailsPatch> details,
    @JsonApiAttribute PatchPresence<List<String>> tags
) {}
```

Suppose article `"1"` currently has this resource representation before the update:

```json
{
  "data": {
    "type": "articles",
    "id": "1",
    "attributes": {
      "details": {
        "summary": "Initial summary",
        "language": "en"
      },
      "tags": [
        "jsonapi",
        "java"
      ]
    }
  }
}
```

### Update a single nested member

This request (`detailsUpdateBody`) updates `details.summary` while omitting `details.language` and
`tags`:

```json
{
  "data": {
    "type": "articles",
    "id": "1",
    "attributes": {
      "details": {
        "summary": "Updated summary"
      }
    }
  }
}
```

```java
StructuredArticlePatch detailsPatch = api.patches().readPatch(
    detailsUpdateBody,
    StructuredArticlePatch.class
);
```

Resulting presence state:

```text
detailsPatch.details():            Present(DetailsPatch)
  details.summary():               Present("Updated summary")
  details.language():              Omitted
detailsPatch.tags():               Omitted
```

Inspect both the outer and nested `PatchPresence` states to compute the updated values:

```java
String currentSummary = "Initial summary";
String currentLanguage = "en";

if (detailsPatch.details() instanceof PatchPresence.Present<DetailsPatch>(DetailsPatch details)
        && details != null) {
    currentSummary = switch (details.summary()) {
        case PatchPresence.Omitted<String> ignored -> currentSummary;
        case PatchPresence.Present<String> supplied -> supplied.value();
    };
    currentLanguage = switch (details.language()) {
        case PatchPresence.Omitted<String> ignored -> currentLanguage;
        case PatchPresence.Present<String> supplied -> supplied.value();
    };
}
```

After the application applies the update, `details.summary` is `"Updated summary"`, while the
omitted `details.language` (`"en"`) and `tags` (`["jsonapi", "java"]`) remain unchanged:

```json
{
  "data": {
    "type": "articles",
    "id": "1",
    "attributes": {
      "details": {
        "summary": "Updated summary",
        "language": "en"
      },
      "tags": [
        "jsonapi",
        "java"
      ]
    }
  }
}
```

Supplying `"details": {}` binds to `Present(new DetailsPatch(Omitted, Omitted))`—a present empty
object with zero nested changes, not a request to clear `summary` and `language`. Supplying
`"details": null` binds the outer attribute to `Present(null)`.

### Replace a collection attribute atomically

This request (`clearTagsBody`) omits `details` and supplies an empty array for `tags`:

```json
{
  "data": {
    "type": "articles",
    "id": "1",
    "attributes": {
      "tags": []
    }
  }
}
```

```java
StructuredArticlePatch tagsPatch = api.patches().readPatch(
    clearTagsBody,
    StructuredArticlePatch.class
);
```

Resulting presence state:

```text
tagsPatch.details(): Omitted
tagsPatch.tags():    Present([])
```

Intended result: `details` keeps its existing `summary` and `language`, while the entire `tags`
list `["jsonapi", "java"]` is replaced with `[]`. Supplying `"tags": ["release"]` similarly
replaces the whole list with `["release"]`.

!!! warning "Containers are whole replacements"
    Lists, sets, arrays, maps, and relationship linkage are replaced as a unit. An atomic `[]` or
    `{}` replaces the entire container with an empty one, and `null` is not a generic remove
    operation. Value collections replaced together (such as string keywords) fit well as
    attributes; when items have their own identity or independent lifecycle, model them as
    JSON:API relationships instead.

## Validate endpoint identity before binding {#validate-endpoint-identity}

`readPatch` validates that the payload is a single-resource update document matching the DTO's
`@JsonApiResource` type. When an endpoint URL also specifies the target resource (for example,
`PATCH /articles/1`), validate the document against an `EndpointIdentity` before binding:

```java
import com.kazforge.jsonapi.core.aggregate.ValidationContext;
import com.kazforge.jsonapi.core.validation.DocumentUsage;
import com.kazforge.jsonapi.core.validation.EndpointIdentity;
import com.kazforge.jsonapi.document.DocumentReadContext;
import com.kazforge.jsonapi.document.PrimaryDataKind;

EndpointIdentity target = new EndpointIdentity("articles", "1");
ValidationContext updateValidation = ValidationContext.defaults()
    .withDocumentUsage(DocumentUsage.UPDATE_REQUEST)
    .withExpectedEndpointIdentity(target);
DocumentReadContext context = DocumentReadContext.of(
    updateValidation,
    PrimaryDataKind.RESOURCE
);

var validated = api.documents().read(clearSubtitleBody, context);
ArticlePatch checked = api.patches().bindPatch(validated, ArticlePatch.class);
```

`checked` has the same presence states as `clearSubtitlePatch`. If the payload's `type` or `id`
does not match `target`, `api.documents().read(...)` fails with `ENDPOINT_IDENTITY_MISMATCH` before
binding runs. `bindPatch` assumes a pre-validated update document and does not re-parse or
revalidate it.

## Low-level commands {#low-level-commands}

For generic infrastructure or change inspection without a dedicated `PatchPresence` DTO, bind
only the supplied changes against a regular resource DTO such as `ArticleView` from
[Resources](resources.md#write-relationships-read-linkage):

```java
import com.kazforge.jsonapi.patch.PatchCommand;

PatchCommand<ArticleView> command = api.patches().readCommand(
    titleUpdateBody,
    ArticleView.class
);
Object identity = command.identity();
```

For `titleUpdateBody`, `identity` is `"1"` and `command.changes()` contains one entry: an
`AttributeChange` for `"title"` with value `"A practical JSON:API guide"`. Omitted members such as
`author` do not appear in `command.changes()`.

<!-- x-release-please-start-version -->
[`bindCommand`](https://www.javadocs.dev/com.kazforge/jsonapi-java-api/0.4.0/com/kazforge/jsonapi/api/JsonApiPatches.html#bindCommand(com.kazforge.jsonapi.core.model.JsonApiDocument,java.lang.Class))
is the pre-validated counterpart to `readCommand`, binding a `JsonApiDocument` into a
`PatchCommand<T>` without re-parsing or revalidation. When an ordinary DTO contains a traversable
bean attribute or resource/relationship `meta`, the low-level command represents supplied nested
changes as a recursive
[`StructuredPatch`](https://www.javadocs.dev/com.kazforge/jsonapi-java-api/0.4.0/com/kazforge/jsonapi/patch/StructuredPatch.html)
while keeping containers atomic.
<!-- x-release-please-end -->

## Validation and mapping boundaries

Both PATCH paths enforce JSON:API update-request rules before binding and report failures through
the standard [diagnostic families](diagnostics.md):

- **Update document validation (`JsonApiDocumentReadException`):** Primary `data` must be one
  resource object with `type` and `id` (never `lid` alone, `null`, an identifier, or an array).
  Supplied primary relationships must contain `data`.
- **Typed PATCH strictness (`JsonApiMappingException`):** `readPatch` and `bindPatch` require
  patchable properties to declare `PatchPresence<T>` and reject unknown supplied JSON members
  (`UNKNOWN_PATCH_MEMBER`), wrapped `@JsonApiId` declarations, or nested PATCH types that mix
  `PatchPresence` and unwrapped properties (`INVALID_PATCH_PROPERTY_TYPE`).
- **Low-level command projection:** `readCommand` and `bindCommand` skip unknown supplied JSON
  members on the target DTO, but still fail if a supplied mapped member cannot be converted.
- **Null and conversion rules:** Recursion and member names follow your configured Jackson mapper.
  Custom property-scoped conversion can keep a bean-shaped value atomic. Outer attributes may be
  `null`, but object-valued `meta` may not be `null`, and primitive nested targets cannot accept
  `null`.

<!-- x-release-please-start-version -->
See [`JsonApiPatches`](https://www.javadocs.dev/com.kazforge/jsonapi-java-api/0.4.0/com/kazforge/jsonapi/api/JsonApiPatches.html),
[`PatchPresence`](https://www.javadocs.dev/com.kazforge/jsonapi-java-api/0.4.0/com/kazforge/jsonapi/patch/PatchPresence.html),
[`PatchCommand`](https://www.javadocs.dev/com.kazforge/jsonapi-java-api/0.4.0/com/kazforge/jsonapi/patch/PatchCommand.html),
[`PatchChange`](https://www.javadocs.dev/com.kazforge/jsonapi-java-api/0.4.0/com/kazforge/jsonapi/patch/PatchChange.html),
and [`StructuredPatch`](https://www.javadocs.dev/com.kazforge/jsonapi-java-api/0.4.0/com/kazforge/jsonapi/patch/StructuredPatch.html)
for exact contracts.
<!-- x-release-please-end -->
