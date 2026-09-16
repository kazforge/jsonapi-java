# jsonapi-java-query

Framework- and Jackson-major-neutral parsing of JSON:API query selection. The module parses
`include`, `fields[TYPE]`, and `sort`, while preserving `page`, `filter`, and unknown parameters as
ordered opaque values.

The [`com.kazforge.jsonapi.query`](src/main/java/com/kazforge/jsonapi/query/package-info.java) package
provides [`JsonApiQueryParser`](src/main/java/com/kazforge/jsonapi/query/JsonApiQueryParser.java),
[`JsonApiQuery`](src/main/java/com/kazforge/jsonapi/query/JsonApiQuery.java), exact
[`QueryAllowList`](src/main/java/com/kazforge/jsonapi/query/QueryAllowList.java) policy, sort values,
and stable query diagnostics.

## Usage

```java
JsonApiQuery query = new JsonApiQueryParser().parseRaw(
    "?include=comments.author&fields[articles]=title,comments&sort=-created"
        + "&page[number]=2&filter[articles][status]=draft");

query.selection().includePaths();
query.selection().fieldsets();
query.sortFields();
query.pageParameters();
query.filterParameters();
```

`parseDecoded(Map<String, List<String>>)` is the canonical framework-integration seam. `parseRaw`
handles an optional leading `?`, query delimiters, and UTF-8 form decoding before delegating to the
same decoded path. Parameter order and repeated-value order are preserved.

Include paths, field names, and sort fields use exact JSON:API tokens; they are neither trimmed nor
resolved against Java properties. Explicit empty `include=` and `fields[type]=` remain distinct from
omission. `QueryAllowList` can reject non-permitted paths, fields, and sort names, but a successful
parse is only a selection upper bound.

## Boundary

This module does not interpret filters or pagination, execute persistence queries, authorize
requests, bind Java properties, apply representation policy, or choose HTTP behavior. It has no
Spring, servlet, ORM, or Jackson-major dependency. Query diagnostics remain independent from codec,
mapping, and transport diagnostics.

See the [architecture overview](../docs/architecture.md),
[conformance checklist](../docs/conformance.md), and
[ADR-007](../docs/adr/007-module-boundaries.md).
