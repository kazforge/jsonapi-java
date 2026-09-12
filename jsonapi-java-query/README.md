# jsonapi-java-query

Framework- and Jackson-major-neutral parsing of JSON:API query selection. The module parses the
standardized `include`, `fields`, and `sort` families, preserves `page` and `filter` parameters as
opaque ordered values, and retains other parameters for application policy.

## Package

| Package | Role |
|---------|------|
| `com.kazforge.jsonapi.query` | Immutable parsed query values, exact allow-lists, and stable query diagnostics |

## Minimal usage

```java
JsonApiQueryParser parser = new JsonApiQueryParser();
JsonApiQuery query = parser.parseRaw(
    "?include=comments.author&fields[articles]=title,comments&sort=-created"
        + "&page[number]=2&filter[articles][status]=draft");

query.selection().includePaths();
query.selection().fieldsets();
query.sortFields();
query.pageParameters();
query.filterParameters();
```

The decoded-multimap seam is equivalent:

```java
Map<String, List<String>> parameters = new LinkedHashMap<>();
parameters.put("include", List.of("comments.author"));
parameters.put("fields[articles]", List.of("title", "comments"));
parameters.put("sort", List.of("-created"));

JsonApiQuery query = new JsonApiQueryParser().parseDecoded(parameters);
```

Raw input strips one optional leading `?`, skips empty `&` segments, separates at the first `=`,
and uses UTF-8 form decoding. A missing `=` supplies an empty value. Malformed percent escapes
produce `JsonApiQueryException` with `MALFORMED_ENCODING`; ill-formed UTF-8 bytes follow the JDK
form-decoder replacement behavior.

`include` accepts comma-separated relationship paths and preserves an explicit empty `include=`
request. `fields[TYPE]` accepts comma-separated JSON:API member names and preserves an explicit
empty fieldset. `sort` returns ordered `SortField` values; a leading `-` selects descending order.
No values are trimmed or resolved against Java property names. Valid `page[...]` and `filter[...]`
families, including nested and empty brackets, remain opaque ordered maps, as do unrecognized
parameters.

## Allow-lists

An overload accepting `QueryAllowList` applies exact allow-lists to every named selection category:

```java
QueryAllowList allowList = QueryAllowList.of(
    Set.of("comments.author"),
    Set.of("created"),
    Map.of("articles", Set.of("title", "comments")));

JsonApiQuery query = parser.parseRaw(
    "include=comments.author&fields[articles]=title&sort=-created", allowList);
```

An empty allow-list rejects every non-empty named selection in its category. `include=` and
`fields[articles]=` remain valid because they name no path or field. Parsing without an allow-list
is syntax-only; endpoint authorization and application policy remain outside this module.

## Non-goals

This module does not choose HTTP responses, interpret filters or pagination, execute persistence
queries, authorize requests, bind Java properties, or depend on Spring, a servlet API, an ORM, or
either Jackson major. A successful parse is a selection upper bound only. Applications still apply
`RepresentationPolicy` when mapping resources; its default include policy denies traversal.

## Further reading

- [Architecture overview](../docs/architecture.md)
- [Conformance checklist](../docs/conformance.md)
- [ADR-007 — Module boundaries](../docs/adr/007-module-boundaries.md)
- [ADR-010 — Architectural tests](../docs/adr/010-architectural-tests.md)
- [Root agent workflow](../AGENTS.md)

## For contributors / agents

- The parser's decoded multimap path is canonical. Keep raw decoding limited to delimiter handling
  and UTF-8 form decoding before invoking it.
- Preserve insertion order, repeated-value order, and absent versus explicit-empty include state.
- Query diagnostics are independent of codec and mapping diagnostics and never select transport
  behavior.
- Production code remains `@NullMarked`; the only non-JDK production dependency is the public
  `MemberNames` validator transitively exported by `jsonapi-java-jackson-api`.
- Tests mirror the query package and the module has an ArchUnit allow-list for its production
  dependency boundary.
