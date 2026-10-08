# Configuration

Use `jsonApi(mapper)` when the [default runtime](getting-started.md#configure-a-runtime) is enough:
ordinary identifier strings, built-in flat linkage targets, no include traversal, no link decorators,
and no default `jsonapi` object. Use the builder when your application needs to change one of those
choices. Both Jackson majors expose the same five optional builder settings.

## Choose the configuration scope

| Application-lifetime runtime | One operation or request |
|------------------------------|--------------------------|
| Configured native Jackson `JsonMapper` | Include paths and sparse fieldsets |
| Identifier conversion and custom linkage mappers | `ResourceWriteOptions` selection and document envelope |
| Representation policy and traversal limits | Expected update endpoint identity |
| Resource link decorators and optional `jsonapi.version` default | Explicit document read/validation context |

Configure Jackson and shared collaborators before constructing the runtime. Reuse the resulting
immutable, thread-safe `JsonApi`; do not rebuild it for each request. Shared callbacks must themselves
be safe for concurrent invocation. The builder is mutable setup state, not a request container.

The examples below are **independent recipes**. Start each with a fresh `builder` from the matching
bootstrap tab; they are not steps in an all-options configuration.

## Configure Jackson first

Jackson remains authoritative for property discovery, visibility, external names, creators, mix-ins,
serializers/deserializers, and value conversion. JSON:API annotations assign roles; they do not make
an ignored Jackson property visible or automatically turn unannotated properties into attributes.

For example, if your DTO uses `bodyText` but your wire convention is `body_text`, configure Jackson's
naming strategy before creating the runtime:

=== "Jackson 3"

    ```java
    import com.kazforge.jsonapi.api.JsonApi;
    import com.kazforge.jsonapi.jackson3.JsonApiJackson3;
    import tools.jackson.databind.PropertyNamingStrategies;
    import tools.jackson.databind.json.JsonMapper;

    JsonMapper mapper = JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .build();
    var builder = JsonApiJackson3.builder(mapper);
    ```

=== "Jackson 2"

    ```java
    import com.kazforge.jsonapi.api.JsonApi;
    import com.kazforge.jsonapi.jackson2.JsonApiJackson2;
    import com.fasterxml.jackson.databind.PropertyNamingStrategies;
    import com.fasterxml.jackson.databind.json.JsonMapper;

    JsonMapper mapper = JsonMapper.builder()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .build();
    var builder = JsonApiJackson2.builder(mapper);
    ```

With no optional builder settings, `JsonApi api = builder.build()` uses the same defaults as
`jsonApi(mapper)`. For a property declared `@JsonApiAttribute String bodyText`, the default Jackson
naming produces `"attributes":{"bodyText":"A draft"}`; this mapper produces
`"attributes":{"body_text":"A draft"}`. Selections and policy allowances must then use `body_text`.
Register application value serializers/deserializers on Jackson before runtime construction too;
attribute conversion follows that configured mapper rather than a separate JSON:API conversion rule.

Runtime construction derives the mappers it needs **without mutating the caller mapper**. Finish
configuration first; do not rely on later caller-mapper changes propagating to an existing runtime.

## Identifier conversion

The default `identifierConverter` calls `toString()` on writes. On reads it returns the wire string
unchanged, then the binder uses configured Jackson to coerce it to the declared Java identity type.
Keep that default unless your application needs a different identifier wire format.

For example, an application can expose its internal string `7` as `ref-7`. Supply both directions,
not just a write-side lambda:

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
```

Using the `Article` from [getting started](getting-started.md#read-and-write-an-article), writing
`new Article("7", "A draft")` now emits `"id":"ref-7"`; reading it into `Article.class` returns id
`7`. The same converter serves `id` and `lid` across the runtime's mapped resource and PATCH paths,
but those remain separate protocol roles with no fallback between them. Choose a format appropriate
for every mapped identity, not an article-only prefix on a runtime also used for people.

## Custom relationship linkage targets

The `linkageMappers` registry is empty by default. Flat reads can use `ResourceIdentifier` and its
built-in optional/collection/array shapes. Register a mapper when an application DTO instead needs
its own reference type—for example, an `AuthorReference`, not a hydrated `Person`.

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

Use the matching major's `RelationshipLinkageMapper` import:
`com.kazforge.jsonapi.jackson3.mapping.RelationshipLinkageMapper` for Jackson 3, or
`com.kazforge.jsonapi.jackson2.mapping.RelationshipLinkageMapper` for Jackson 2. The rest of this
to-one example is shared:

```java
import com.kazforge.jsonapi.core.model.RelationshipData;
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
```

Reading the [article document](resources.md#write-relationships-read-linkage) into
`ArticleReferenceView.class` now gives `new AuthorReference("people", "p1")` instead of a
`ResourceIdentifier`. Registration is keyed by the relationship target class, not the owning article
class or the wire resource type. This mapper is deliberately for to-one author references; support
other linkage shapes explicitly if you use the same target in to-many properties.

This callback is the one native-major boundary among these settings: `targetType` is the adapter's
Jackson `JavaType`, even when a lambda does not use it. The mapper supplies values for flat binding
and PATCH projection, not a write-side relationship serializer. It does not read `included`, fetch
persisted people, or hydrate an object graph. Implement any application lookup explicitly; to include
already available author objects on writes, use representation selection and policy instead.

## Representation policy

Set `representationPolicy` when you need to permit inclusion, restrict requested field names, or
change traversal bounds. Policy decides what a request may select; it does not select anything by
itself.

| Setting | Default | Effect |
|---------|---------|--------|
| `includePolicy` | `IncludePolicy.denyAll()` | Rejects requested include traversal; ordinary relationship linkage is unaffected |
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
[apply selection with policy](query-and-representation.md#apply-selection-with-policy) to pass the
selection to `selectedApi` and observe the result.

Include allowances pair the **owning resource type** with the relationship's **wire name**. A nested
path needs permission at every segment: `author.articles` would also need the `people` / `articles`
allowance and a depth of at least 2. Field allowances pair resource type with field wire name.
An unknown or denied requested name fails; the library does not quietly remove it from the request.
Inclusion traverses supplied objects, not a persistence query.

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
```

A write of article `1` previously had no resource links; it now adds
`"links":{"self":"/articles/1"}` inside `data`. This example assumes path-safe article ids;
your application owns URI construction and encoding.

Lookup is an exact match on the effective raw domain class; subclasses need their own registration.
Decoration is additive and limited to resource links and links on already-mapped relationships. It
does not create relationships or change linkage, meta, inclusion, or document-level links.

Relationship decoration keys use the **logical Java/Jackson property identity**, unlike representation
selections and allowances. For a property `author` renamed to `article-author` on the wire, use
`author` in `ResourceDecoration.builder().relationship(...)`; mapping resolves the wire name.

Callbacks are retained and reused, not isolated per call. Keep this shared decorator safe for
concurrent invocation; do not store mutable request state in it.

## Optional document version default

By default, the runtime injects no `jsonapi` object. Set `jsonApiVersion` if your resource documents
should advertise a protocol version when the write does not explicitly supply that object:

```java
JsonApi api = builder.jsonApiVersion("1.1").build();
```

Writing an article now adds `"jsonapi":{"version":"1.1"}` at the top level. This default applies to
resource-facet writes, including collections and create/update authoring. It does **not** apply to
raw-document or minimal relationship-linkage writes.

A per-write envelope takes precedence. For example, to emit an explicit empty `jsonapi` object:

```java
import com.kazforge.jsonapi.api.ResourceWriteOptions;
import com.kazforge.jsonapi.core.model.JsonApiObject;
import com.kazforge.jsonapi.document.DocumentEnvelope;
import java.util.Map;

JsonApiObject explicit = new JsonApiObject(null, null, null, null, Map.of());
ResourceWriteOptions options = ResourceWriteOptions.defaults()
    .withEnvelope(new DocumentEnvelope(null, null, explicit));

String json = api.resources().writeOne(new Article("1", "A draft"), options);
```

The result has `"jsonapi":{}`, not `"jsonapi":{"version":"1.1"}`. An explicit object replaces the
default completely—there is no merge. Leaving the envelope's `jsonapi` component absent (`null`)
inherits the configured default, even if the envelope supplies other members.

This is document `jsonapi.version`, not the library artifact version, business API versioning, HTTP
content negotiation, or extension/profile support.

## Supply operation-scoped values at the call

`ResourceWriteOptions` contains only selection and envelope, never representation policy. Its
defaults request neither includes nor fieldsets and supply no envelope members. Omitted selections
remain distinct from explicitly empty requests; they do not alter the runtime's policy.

Keep task details on their owning pages:

| Per-operation value | Task |
|---------------------|------|
| Include paths, sparse fieldsets, and `ResourceWriteOptions` | [Apply selection with policy](query-and-representation.md#apply-selection-with-policy) |
| Document links, meta, and explicit `jsonapi` | [Top-level state](resources.md#top-level-state) |
| Expected identity for update writes | [Author an update document](resources.md#author-an-update-document) |
| Expected identity for validated PATCH reads | [Typed update presence](patches.md#typed-update-presence) |
| Explicit document read/validation context | [Preserve document state](relationships-and-documents.md#preserve-document-state) |

For requirements beyond these Level-1 settings, use the
[ordinary-or-advanced decision](relationships-and-documents.md#ordinary-or-advanced) and the relevant
source Javadoc rather than rebuilding the ordinary pipeline yourself.
