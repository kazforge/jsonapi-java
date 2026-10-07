# PATCH

A partial update is not a complete read/write DTO. Use the configured `JsonApi api` from
[getting started](getting-started.md#configure-a-runtime) and a separate application-owned schema
whose patchable members use `PatchPresence<T>`.

## Typed update presence

Nest these public records in your example class; put operation snippets in a method:

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
    @JsonApiRelationship PatchPresence<ResourceIdentifier> author) {}
```

```java
String request = """
    {"data":{"type":"articles","id":"1","attributes":{"title":null}}}
    """;
ArticlePatch patch = api.patches().readPatch(request, ArticlePatch.class);
assert patch.id().equals("1");
assert patch.title().equals(PatchPresence.present(null));
assert patch.author().isOmitted();
```

| Input member | Bound state | Requested change |
|--------------|-------------|------------------|
| Omitted | `PatchPresence.Omitted` | No change |
| Explicit null | `PatchPresence.Present` with a null value | Supplied null, subject to declared conversion/business rules |
| Supplied value | `PatchPresence.Present` with that converted value | Supplied replacement or structured change |

The identity is unwrapped and comes from `id`, not `lid`. Typed PATCH rejects unknown supplied
members and invalid wrapper declarations. An inner `Optional<T>` does not replace the outer
presence marker: configured conversion may turn supplied null into `Optional.empty()`.

Inspect presence before applying application policy:

```java
String action = switch (patch.title()) {
    case PatchPresence.Omitted<String> ignored -> "leave title unchanged";
    case PatchPresence.Present<String> supplied ->
        supplied.value() == null ? "request null title" : "request title: " + supplied.value();
};
assert action.equals("request null title");
```

The library validates and projects the request. You compare endpoint identity, authorize fields,
check business invariants/concurrency, and apply changes. For validated endpoint comparison before
binding, reuse a document:

```java
import com.kazforge.jsonapi.core.aggregate.ValidationContext;
import com.kazforge.jsonapi.core.validation.DocumentUsage;
import com.kazforge.jsonapi.core.validation.EndpointIdentity;
import com.kazforge.jsonapi.document.DocumentReadContext;
import com.kazforge.jsonapi.document.PrimaryDataKind;

var context = DocumentReadContext.of(ValidationContext.defaults()
    .withDocumentUsage(DocumentUsage.UPDATE_REQUEST)
    .withExpectedEndpointIdentity(new EndpointIdentity("articles", "1")), PrimaryDataKind.RESOURCE);
var validated = api.documents().read(request, context);
ArticlePatch checked = api.patches().bindPatch(validated, ArticlePatch.class);
assert checked.equals(patch);
```

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

public record DetailsPatch(PatchPresence<String> summary, PatchPresence<String> language) {}

@JsonApiResource(type = "articles")
public record StructuredArticlePatch(
    @JsonApiId String id,
    @JsonApiAttribute PatchPresence<DetailsPatch> details,
    @JsonApiAttribute PatchPresence<List<String>> tags) {}
```

```java
String structuredRequest = """
    {"data":{"type":"articles","id":"1","attributes":{
      "details":{"summary":"Short version"},"tags":[]}}}
    """;
StructuredArticlePatch structured = api.patches().readPatch(
    structuredRequest, StructuredArticlePatch.class);
var details = (PatchPresence.Present<DetailsPatch>) structured.details();
assert details.value().summary().equals(PatchPresence.present("Short version"));
assert details.value().language().isOmitted();
assert structured.tags().equals(PatchPresence.present(List.of()));
```

!!! warning "Containers are whole replacements"
    Lists, sets, arrays, maps, and relationship linkage are atomic. There is no element-addressed
    collection PATCH. A structured `{}` is a supplied empty object, not clear-all; an atomic `[]`
    or `{}` replaces the entire container with an empty one. Null is not a generic remove operation.

Recursion follows configured Jackson property/conversion semantics. Typed nested shapes reject
unknown members; custom atomic conversion can keep a bean-shaped value atomic. Outer attributes
may be null, but object-valued meta may not. Primitive nested targets cannot accept null.

## Low-level commands

For infrastructure or generic supplied-change handling, project onto an ordinary resource schema.
Reuse `ArticleView` from [resources](resources.md#write-relationships-read-linkage):

```java
import com.kazforge.jsonapi.patch.PatchCommand;

PatchCommand<ArticleView> command = api.patches().readCommand(request, ArticleView.class);
assert command.identity().equals("1");
assert command.changes().size() == 1;
```

Commands contain only supplied mapped changes; they do not construct a complete DTO. Unknown members
are skipped, unlike typed PATCH. Traversable ordinary bean attributes/resource-side meta produce
recursive `StructuredPatch` members, while containers stay atomic. Both paths enforce update shape
and preserve omission versus null; neither applies changes.

See [`JsonApiPatches`](https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-api/src/main/java/com/kazforge/jsonapi/api/JsonApiPatches.java),
[`PatchPresence`](https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-api/src/main/java/com/kazforge/jsonapi/patch/PatchPresence.java),
and [`StructuredPatch`](https://github.com/kazforge/jsonapi-java/blob/main/jsonapi-java-api/src/main/java/com/kazforge/jsonapi/patch/StructuredPatch.java)
for exact contracts.
