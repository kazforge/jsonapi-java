# PATCH

A partial update is not a complete read/write DTO. Use the configured `JsonApi api` from
[getting started](getting-started.md#configure-a-runtime) and a separate application-owned schema
whose patchable members use `PatchPresence<T>`.

## Typed update presence

Declare a schema for the fields your application accepts in an article update:

```java
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.patch.PatchPresence;

@JsonApiResource(type = "articles")
public record ArticlePatch(
    @JsonApiId String id,
    @JsonApiAttribute PatchPresence<String> title,
    @JsonApiRelationship PatchPresence<ResourceIdentifier> author
) {}
```

This request body (`updateBody`) supplies a null title and omits the author:

```json
{
  "data": {
    "type": "articles",
    "id": "1",
    "attributes": {
      "title": null
    }
  }
}
```

Bind it to the PATCH schema:

```java
ArticlePatch patch = api.patches().readPatch(updateBody, ArticlePatch.class);
PatchPresence<String> titleChange = patch.title();
PatchPresence<ResourceIdentifier> authorChange = patch.author();
```

The patch identity is `1`. Its member states are:

```text
title:  present null
author: omitted
```

| Input member | Bound state | Requested change |
|--------------|-------------|------------------|
| Omitted | `PatchPresence.Omitted` | No change |
| Explicit null | `PatchPresence.Present` with a null value | Supplied null, subject to declared conversion/business rules |
| Supplied value | `PatchPresence.Present` with that converted value | Supplied replacement or structured change |

The identity is unwrapped and comes from `id`, not `lid`. Typed PATCH rejects unknown supplied
members and invalid wrapper declarations. An inner `Optional<T>` does not replace the outer
presence marker: configured conversion may turn supplied null into `Optional.empty()`.

After authorizing the update, use presence to choose the proposed title without changing domain state:

```java
String currentTitle = "Working with JSON:API";
String proposedTitle = switch (titleChange) {
    case PatchPresence.Omitted<String> ignored -> currentTitle;
    case PatchPresence.Present<String> supplied -> supplied.value();
};
```

For this request, `proposedTitle` is null. Your business rules decide whether a null title is allowed.
An omitted title would preserve `currentTitle` instead.

The application provides the expected endpoint identity, and the library validates the update against
it. The application still owns authorization, business rules, concurrency, and applying changes. To
validate endpoint identity before binding:

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

var validated = api.documents().read(updateBody, context);
ArticlePatch checked = api.patches().bindPatch(validated, ArticlePatch.class);
```

`checked` has the same presence states as the earlier patch. A type or id that does not match `target`
fails endpoint-identity validation before binding.

`bindPatch`/`bindCommand` assume an already validated update document; they do not revalidate it.

## Relationships replace linkage

Omitted relationships request no change. A supplied relationship must have `data`: to-one null,
one identifier, or a to-many identifier array. An empty array replaces linkage with an empty
collection; it is not omission. Neither PATCH path reads `included` or performs relationship lookup.
Identifier meta participates in whole-linkage replacement, not a nested graph edit.

## Structured attributes and containers

Nested omission preservation is a library feature beyond JSON:API's attribute-level presence,
not JSON Merge Patch. Opt in to typed recursion with a nested shape made of presence wrappers:

```java
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

This request body (`structuredBody`) changes only the summary within `details`, and supplies an
empty replacement for `tags`:

```json
{
  "data": {
    "type": "articles",
    "id": "1",
    "attributes": {
      "details": {
        "summary": "Short version"
      },
      "tags": []
    }
  }
}
```

```java
StructuredArticlePatch structured = api.patches().readPatch(
    structuredBody,
    StructuredArticlePatch.class
);
PatchPresence<DetailsPatch> detailsChange = structured.details();
PatchPresence<List<String>> tagsChange = structured.tags();
```

The projected member states are:

```text
details.summary:  present "Short version"
details.language: omitted
tags:             present empty list
```

!!! warning "Containers are whole replacements"
    Lists, sets, arrays, maps, and relationship linkage are atomic. There is no element-addressed
    collection PATCH. A structured `{}` is a supplied empty object, not a clear-all request; an atomic `[]`
    or `{}` replaces the entire container with an empty one. Null is not a generic remove operation.

Recursion follows configured Jackson property/conversion semantics. Typed nested shapes reject
unknown members; custom atomic conversion can keep a bean-shaped value atomic. Outer attributes
may be null, but object-valued meta may not. Primitive nested targets cannot accept null.

## Low-level commands

For infrastructure or generic supplied-change handling, project onto an ordinary resource schema.
Reuse `ArticleView` from [resources](resources.md#write-relationships-read-linkage):

```java
import com.kazforge.jsonapi.patch.PatchCommand;

PatchCommand<ArticleView> command = api.patches().readCommand(
    updateBody,
    ArticleView.class
);
Object identity = command.identity();
```

For the null-title request above, `identity` is `1` and the command contains one supplied change:
the title's explicit null. There is no author change.

Commands contain only supplied mapped changes; they do not construct a complete DTO. Unknown members
are skipped, unlike typed PATCH. Traversable ordinary bean attributes/resource-side meta produce
recursive `StructuredPatch` members, while containers stay atomic. Both paths enforce update shape
and preserve omission versus null; neither applies changes.

See [`JsonApiPatches`](https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-api/src/main/java/com/kazforge/jsonapi/api/JsonApiPatches.java),
[`PatchPresence`](https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-api/src/main/java/com/kazforge/jsonapi/patch/PatchPresence.java),
and [`StructuredPatch`](https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-api/src/main/java/com/kazforge/jsonapi/patch/StructuredPatch.java)
for exact contracts.
