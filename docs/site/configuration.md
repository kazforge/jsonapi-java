---
title: "Configure the JSON:API Jackson runtime"
description: "Configure jsonapi-java with Jackson 3: identifier conversion, relationship linkage mappers, representation policy, and resource link decorators."
---

# Configure the JSON:API Jackson runtime {#configuration}

Use `JsonApiJackson3.jsonApi(mapper)` for the [default runtime](getting-started.md#configure-a-runtime):
default identifier conversion and built-in relationship identifier targets, with include requests
denied, no link decorators, and no default `jsonapi` object. Use `JsonApiJackson3.builder(mapper)`
to change those choices through its five optional application-lifetime settings.

## Choose the configuration scope

| Application-lifetime runtime | One operation or request |
|------------------------------|--------------------------|
| Configured native Jackson `JsonMapper` | Include paths and sparse fieldsets |
| Identifier conversion and custom linkage mappers | `ResourceWriteOptions` selection and document envelope |
| Representation policy and traversal limits | Expected update endpoint identity |
| Resource link decorators and optional `jsonapi.version` default | Explicit document read/validation context |

Configure Jackson and shared collaborators before constructing the runtime. Reuse the resulting
immutable, thread-safe `JsonApi` across requests. Callbacks are retained and reused across calls;
keep them safe for concurrent invocation and keep mutable request state outside them. Use the
mutable builder only during setup.

The sections below are **independent recipes**, not cumulative steps in an all-options runtime.
Start each recipe with a fresh `builder` from the bootstrap setup below, and reuse the
[`Article` record](getting-started.md#read-and-write-an-article) and the
[article/people relationship DTOs](resources.md#write-relationships-read-linkage) unless a recipe
declares its own DTO. Output JSON is formatted for readability.

## Configure Jackson first

Jackson controls property discovery, visibility, JSON property names, creators, mix-ins,
serializers/deserializers, and value conversion. JSON:API annotations assign member roles to those
properties. Jackson-ignored properties stay ignored, and attributes require role annotations.

Suppose an article DTO includes a multi-word `bodyText` attribute:

```java
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiResource;

@JsonApiResource(type = "articles")
public record ArticleDraft(
    @JsonApiId String id,
    @JsonApiAttribute String title,
    @JsonApiAttribute String bodyText
) {}
```

To expose `bodyText` as `body_text` on the wire, configure Jackson's naming strategy before
creating the builder or runtime:

```java
import com.kazforge.jsonapi.api.JsonApi;
import com.kazforge.jsonapi.jackson3.JsonApiJackson3;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

JsonMapper mapper = JsonMapper.builder()
    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    .build();
var builder = JsonApiJackson3.builder(mapper);
JsonApi api = builder.build();

ArticleDraft draft = new ArticleDraft("1", "Working with JSON:API", "A draft");
String draftJson = api.resources().writeOne(draft);
```

With no optional builder settings, `builder.build()` uses the same defaults as
`JsonApiJackson3.jsonApi(mapper)`. Default Jackson naming would write `bodyText` as `"bodyText"`;
with `SNAKE_CASE`, `draftJson` uses `"body_text"`:

```json
{
  "data": {
    "type": "articles",
    "id": "1",
    "attributes": {
      "title": "Working with JSON:API",
      "body_text": "A draft"
    }
  }
}
```

Reading `draftJson` with `api.resources().readOne(draftJson, ArticleDraft.class)` binds
`"body_text"` back to `bodyText`. Sparse-fieldset selections and representation-policy allowances
must then use the wire name `body_text`, not the Java property name `bodyText`. Register
application value serializers and deserializers on Jackson before runtime construction too;
attribute conversion follows that configured mapper rather than a separate JSON:API conversion rule.

Runtime construction derives the mappers it needs **without mutating the caller mapper**. Finish
configuration first; do not rely on later caller-mapper changes propagating to an existing runtime.

## Identifier conversion

The default `identifierConverter` calls `toString()` on writes. On reads it returns the wire string
unchanged, then the binder uses configured Jackson to coerce it to the declared Java identity type.
Keep that default unless your application needs a different identifier wire format.

For example, an application can expose its internal identifier `"7"` as `"ref-7"` on the wire.
Supply both directions, not just a write-side conversion:

```java
import com.kazforge.jsonapi.mapping.IdentifierConverter;

IdentifierConverter references = new IdentifierConverter() {
    @Override
    public String convert(Object value) {
        return value == null ? null : "ref-" + value;
    }

    @Override
    public Object parse(String wire) {
        if (wire == null || !wire.startsWith("ref-") || wire.length() == 4) {
            throw new IllegalArgumentException("Expected a ref- identifier");
        }
        return wire.substring(4);
    }
};
JsonApi api = builder.identifierConverter(references).build();

Article article = new Article("7", "Working with JSON:API");
String refJson = api.resources().writeOne(article);
```

`refJson` carries `"ref-7"` in `data.id`:

```json
{
  "data": {
    "type": "articles",
    "id": "ref-7",
    "attributes": {
      "title": "Working with JSON:API"
    }
  }
}
```

Read that document back into `Article`:

```java
Article readBack = api.resources().readOne(refJson, Article.class);
```

`readBack.id()` is `"7"`, while `readBack.title()` is `"Working with JSON:API"`. On writes, the
converter formats `id` and `lid` values extracted from mapped domain objects—including related
domain objects written as relationship linkage—while direct `ResourceIdentifier` linkage keeps its
supplied wire identifiers unchanged. On reads and PATCH binding, the configured `parse` method
applies to mapped resource `id` and `lid` properties; reading relationship linkage into
`ResourceIdentifier` keeps the wire identifiers without invoking `parse`. `id` and `lid` remain
separate protocol roles with no fallback between them. Choose a format appropriate for every mapped
domain identity on that runtime, not an article-only prefix on a runtime also used for people.

## Custom relationship linkage targets

The `linkageMappers` registry is empty by default. Flat reads can use `ResourceIdentifier` and its
built-in optional/collection/array shapes. Register a mapper when an application DTO instead needs
its own reference type—for example, an `AuthorReference`, not a fully loaded `Person`. Each public
record below is a separate Java type, normally declared in its own file:

```java
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;

public record AuthorReference(String type, String id) {}

@JsonApiResource(type = "articles")
public record ArticleReferenceView(
    @JsonApiId String id,
    @JsonApiAttribute String title,
    @JsonApiRelationship AuthorReference author
) {}
```

Register a `RelationshipLinkageMapper` for `AuthorReference` and read the
`articleJson` document from [Resources](resources.md#write-relationships-read-linkage):

```java
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.jackson3.mapping.RelationshipLinkageMapper;
import java.util.Map;

RelationshipLinkageMapper authorReferences = (linkage, targetType) -> {
    if (!(linkage instanceof RelationshipData.SingleLinkage single)) {
        throw new IllegalArgumentException("Expected one author reference");
    }
    var identifier = single.identifier();
    return new AuthorReference(identifier.type(), identifier.id());
};
JsonApi api = builder
    .linkageMappers(Map.of(AuthorReference.class, authorReferences))
    .build();

ArticleReferenceView view = api.resources().readOne(
    articleJson,
    ArticleReferenceView.class
);
AuthorReference author = view.author();
```

`author` is `new AuthorReference("people", "p1")` instead of a `ResourceIdentifier`. Registration
is keyed by the relationship target class (`AuthorReference.class`), not the owning article class
or the wire resource type. This mapper is deliberately for to-one author references; support other
linkage shapes explicitly if you use the same target in to-many properties.

This is the only builder setting whose callback uses a Jackson-specific type: `targetType` is
Jackson's `tools.jackson.databind.JavaType`, even when the lambda does not use it. The mapper converts
relationship identifiers into DTO values on reads and when binding PATCH changes. It neither
serializes relationships on writes nor reads `included` or fetches related objects. Your application
owns any lookup; use representation selection and policy to include already available author objects
on writes.

## Representation policy

Set `representationPolicy` to permit inclusion, restrict requested field names, or change traversal
limits. Policy controls what a request may select; pass the actual selection separately on each write.

| Setting | Default | Effect |
|---------|---------|--------|
| `includePolicy` | `IncludePolicy.denyAll()` | Rejects requested include traversal; relationship identifiers are still written |
| `fieldPolicy` | `FieldPolicy.allowAll()` | Permits every selected **mapped** sparse-fieldset name, not arbitrary unknown names |
| `maxIncludeDepth` | `10` | Maximum number of segments in an include path (`author` is 1; `author.articles` is 2) |
| `maxIncludedResources` | `100` | Maximum distinct resources emitted in `included`, not the primary-resource count |

Both limits accept zero but reject negative values. Depth zero rejects any nonempty include path;
count zero permits no included resources. Exceeding a limit fails mapping rather than silently
truncating the response. Repeated encounters of the same included identity do not consume another
slot.

For the [article/people model](resources.md#write-relationships-read-linkage), allow one-hop author
inclusion and a small requested field vocabulary:

```java
import com.kazforge.jsonapi.representation.FieldAllowance;
import com.kazforge.jsonapi.representation.FieldPolicy;
import com.kazforge.jsonapi.representation.IncludePolicy;
import com.kazforge.jsonapi.representation.RelationshipAllowance;
import com.kazforge.jsonapi.representation.RepresentationPolicy;
import java.util.Set;

RepresentationPolicy policy = RepresentationPolicy.defaults()
    .withIncludePolicy(IncludePolicy.allowing(Set.of(
        RelationshipAllowance.of("articles", "author")
    )))
    .withFieldPolicy(FieldPolicy.allowing(Set.of(
        FieldAllowance.of("articles", "title"),
        FieldAllowance.of("articles", "author"),
        FieldAllowance.of("people", "name")
    )))
    .withMaxIncludeDepth(1)
    .withMaxIncludedResources(10);
JsonApi selectedApi = builder.representationPolicy(policy).build();
```

Before this change, `include=author` fails under the default deny-includes policy. Now that request
is permitted, and `fields[articles]=title,author` is permitted too. Without an include selection,
the author is still only linkage, not an included resource. See
[apply selection with policy](query-and-representation.md#apply-selection-with-policy) to pass a
parsed selection to `selectedApi` and observe the resulting JSON document.

Include allowances pair the **owning resource type** with the relationship's **wire name**. A nested
path needs permission at every segment: `author.articles` would also need the `people` / `articles`
allowance and a depth of at least 2. Field allowances pair resource type with field wire name.
An unknown or denied requested name fails rather than being silently removed. Inclusion traverses
the objects your application supplies.

!!! warning "Field policy is not global redaction or authorization"
    Field policy checks only names in a supplied sparse fieldset. **With no fieldset, mapped fields
    are still written**, even with `FieldPolicy.denyAll()`. An explicitly empty fieldset selects no
    attributes or relationships and performs no per-field checks. Identity and resource meta are
    separate from field selection. Use appropriate DTOs and application authorization for sensitive
    data; do not use a field allow-list as an unconditional output filter.

## Resource link decorators

The `decorators` registry is empty by default. Use it when mapped resources need application-owned
links without putting endpoint construction into DTO annotations. For the `Article` from
[getting started](getting-started.md#read-and-write-an-article):

```java
import com.kazforge.jsonapi.core.model.Link;
import com.kazforge.jsonapi.core.model.Links;
import com.kazforge.jsonapi.mapping.ResourceDecoration;
import com.kazforge.jsonapi.mapping.ResourceDecoratorRegistry;
import java.util.Map;

ResourceDecoratorRegistry decorators = ResourceDecoratorRegistry.builder()
    .register(Article.class, article -> ResourceDecoration.ofLinks(
        Links.ofLinks(Map.of(
            "self", new Link.StringLink("/articles/" + article.id())
        ))
    ))
    .build();
JsonApi api = builder.decorators(decorators).build();

String decoratedJson = api.resources().writeOne(
    new Article("1", "Working with JSON:API")
);
```

Writing article `"1"` now adds a resource-level `links` object inside `data`:

```json
{
  "data": {
    "type": "articles",
    "id": "1",
    "attributes": {
      "title": "Working with JSON:API"
    },
    "links": {
      "self": "/articles/1"
    }
  }
}
```

This example assumes path-safe article IDs; your application owns URI construction and encoding.

Lookup is an exact match on the effective raw domain class; subclasses need their own registration.
Decoration is additive and limited to resource links and links on already-mapped relationships. It
does not create relationships or change linkage, meta, inclusion, or document-level links.

Relationship decoration keys use the **logical Java/Jackson property identity**, unlike representation
selections and allowances. For a property `author` renamed to `article-author` on the wire, use
`author` in `ResourceDecoration.builder().relationship(...)`; mapping resolves the wire name.

## Optional document version default

By default, the runtime injects no `jsonapi` object. Set `jsonApiVersion` if your resource documents
should advertise a protocol version when the write does not explicitly supply that object:

```java
JsonApi api = builder.jsonApiVersion("1.1").build();

String versionedJson = api.resources().writeOne(
    new Article("1", "Working with JSON:API")
);
```

`versionedJson` includes `"jsonapi": {"version": "1.1"}` at the document root:

```json
{
  "data": {
    "type": "articles",
    "id": "1",
    "attributes": {
      "title": "Working with JSON:API"
    }
  },
  "jsonapi": {
    "version": "1.1"
  }
}
```

This default applies to `api.resources()` writes, including collections and create/update authoring.
`api.documents()` and `api.relationships()` writes do not inherit this default.

A per-write `DocumentEnvelope` can either inherit or replace that configured default:

- When the envelope's `jsonapi` component is absent (`null`)—such as `ResourceWriteOptions.defaults()`
  or an envelope that supplies only `meta` or `links`—the write inherits the configured
  `{"version": "1.1"}` default.
- When the envelope supplies an explicit `JsonApiObject`, it replaces the configured default
  completely (with no merge). For example, `JsonApiObject.empty()` writes an empty `jsonapi`
  object for that call:

```java
import com.kazforge.jsonapi.api.ResourceWriteOptions;
import com.kazforge.jsonapi.core.model.JsonApiObject;
import com.kazforge.jsonapi.document.DocumentEnvelope;

JsonApiObject explicit = JsonApiObject.empty();
ResourceWriteOptions options = ResourceWriteOptions.defaults()
    .withEnvelope(DocumentEnvelope.ofJsonApi(explicit));

String json = api.resources().writeOne(new Article("1", "Working with JSON:API"), options);
```

In `json`, the top-level `"jsonapi"` member is `{}` rather than `{"version": "1.1"}`, while
`"data"` is unchanged.

`jsonapi.version` advertises the document's protocol version. It does not configure the library
artifact version, business API versioning, HTTP content negotiation, or extension/profile support.

## Supply operation-scoped values at the call

`ResourceWriteOptions` carries selection and envelope; representation policy belongs to the runtime.
Its defaults leave include requests, fieldsets, and envelope members absent. Omitted selections
remain distinct from explicitly empty requests.

Keep task details on their owning pages:

| Per-operation value | Task |
|---------------------|------|
| Include paths, sparse fieldsets, and `ResourceWriteOptions` | [Apply selection with policy](query-and-representation.md#apply-selection-with-policy) |
| Document links, meta, and explicit `jsonapi` | [Top-level state](resources.md#top-level-state) |
| Expected identity for update writes | [Author an update document](resources.md#author-an-update-document) |
| Expected identity for validated PATCH reads | [Validate endpoint identity](patches.md#validate-endpoint-identity) |
| Explicit document read/validation context | [Preserve document state](relationships-and-documents.md#preserve-document-state) |

For requirements beyond these `JsonApi` settings, see
[choose an application or Jackson-specific API](relationships-and-documents.md#ordinary-or-advanced)
and the relevant Javadoc.
