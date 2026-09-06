# jsonapi-java-jackson2

Jackson 2 implementation of the validated JSON:API document codec: validating and writing, plus
token-driven reading of, [JSON:API v1.1](https://jsonapi.org/) documents with deterministic wire
semantics.

> This is a Jackson 2 parity artifact. It currently holds the document writer and the validated
> document reader; domain mapping, flat binding, presence-aware PATCH, and the Level-1 configured
> runtime follow in later parity stories. The Jackson 3 module
> ([jsonapi-java-jackson3](../jsonapi-java-jackson3/README.md)) owns the full capability set
> today.

## Packages

| Package                                        | Role                                                                  |
|------------------------------------------------|-----------------------------------------------------------------------|
| `io.github.kazemek.jsonapi.jackson2`           | Public codec factories (`JsonApiJackson2`), validate-then-emit `JsonApiDocumentWriter`, and token-driven `JsonApiDocumentReader` |
| `io.github.kazemek.jsonapi.jackson2.internal`  | Streaming document serializer, wire emission, and token-driven wire decoding; not public API |
| `io.github.kazemek.jsonapi.jackson.*`          | Public Jackson-major-neutral API contracts (in `jsonapi-java-jackson-api`): `api`, `document`, `mapping`, `patch`, `representation`, `diagnostic` |

Validation policy, read policy, provenance values, and diagnostics (`ValidationContext`,
`DocumentReadContext`, `MappedDocument`, `JsonApiValidationException`,
`JsonApiDocumentReadException`) live in `jsonapi-java-core` and `jsonapi-java-jackson-api` and are
imported from there; this module holds only the Jackson 2-bound writer and its emission
implementation plus the Jackson 2-bound reader and its token-driven decoding implementation.

## Minimal usage

```java
import com.fasterxml.jackson.databind.json.JsonMapper;

JsonMapper callerMapper = JsonMapper.builder().build();

JsonApiDocumentWriter writer = JsonApiJackson2.writer(callerMapper);
String json = writer.writeValueAsString(document);

JsonApiDocumentReader reader =
    JsonApiJackson2.reader(callerMapper, DocumentReadContext.resourceDefaults());
JsonApiDocument roundTrip = reader.readValue(json);
```

The writer validates a `JsonApiDocument` against its bound `ValidationContext` before any
generator output starts, so validation failure cannot leave a partially written document. It
offers five output forms for plain documents and the same five for `MappedDocument` values:

```java
String json = writer.writeValueAsString(document);
byte[] bytes = writer.writeValueAsBytes(document);
writer.writeValue(outputStream, document);
writer.writeValue(writer_, document);
writer.writeValue(generator, document);
```

Sinks are caller-owned: an `OutputStream` or `Writer` passed to `writeValue` is not closed — only
the generator created for the call is closed — and output is fully visible without an explicit
flush. A caller-created `JsonGenerator` stays open for the caller to close.

Sparse-fieldset provenance composes into the bound validation policy: writing a `MappedDocument`
composes the mapping's linkage-exemption identities into the bound context before validation, and
callers never translate mapping provenance into a validation context themselves. An empty
exemption set validates exactly like plain document writing, and unrelated full-linkage defects
still fail:

```java
MappedDocument mapped = /* one mapping call in a later mapping story */;
String json = JsonApiJackson2.writer(callerMapper).writeValueAsString(mapped);
```

## Construction policy

The canonical seam follows ADR-016: a fully configured `JsonMapper` instance, followed by the
capability context:

```java
JsonApiJackson2.writer(mapper);                       // default validation context
JsonApiJackson2.writer(mapper, validationContext);    // canonical mapper-instance form
JsonApiJackson2.reader(mapper, readContext);          // canonical mapper-instance form
```

The writer derives its codec mapper via `rebuild()` and registers only the internal JSON:API
document module; the reader uses the supplied mapper directly for token-driven parsing and never
mutates it. The caller's mapper is never mutated by either path, and the codec mapper is not
public. `JsonMapper.Builder` overloads are intentionally not part of the API. Jackson 2's checked
`JsonProcessingException` mechanics propagate from emission methods unchanged; every reader
overload declares checked `IOException`, Jackson parse failures surface as payload-safe
`JsonApiDocumentReadException` (`MALFORMED_JSON`), and unrelated source I/O propagates unchanged.
Core validation failures stay unchecked `JsonApiValidationException` on writes and become
`JsonApiDocumentReadException` with `ValidationRuleCode` plus JSON Pointer-like path on reads.

## Wire semantics

- Deterministic member order; absence, explicit JSON `null`, and present-empty remain distinct
  wire states; sealed model variants (`NullData`, `NullLinkage`, string vs object links) keep
  their wire shapes.
- Open values (attributes, meta, additional members, link values) accept strings, booleans,
  numbers, lists, maps, and `null`; anything else fails with `IllegalArgumentException`.
- The reader decodes token-driven wire JSON through public core constructors under an explicit
  `DocumentReadContext` (aggregate `ValidationContext` plus explicit `PrimaryDataKind`), then runs
  aggregate validation before returning. Ambiguous `{"type","id"}` and `[]` primary data never
  guess: the bound `PrimaryDataKind` selects resource versus identifier decoding. Whole-input
  overloads (`String`, `byte[]`, `InputStream`) require end-of-input after the document, while the
  caller-parser overload consumes exactly one root value so sequential documents may follow.
  Parsers created by convenience overloads are closed; caller-owned streams and parsers stay open.
- Writer output and corpus documents are cross-checked against the shared passive JSON/schema
  corpus in adapter-owned tests; reader expectations live here, not in shared fixtures.

## Non-goals

Domain mapping, flat DTO binding, typed envelopes, presence-aware PATCH binding, and the Level-1
configured runtime are later Jackson 2 parity stories. HTTP `fields[TYPE]` parsing, field
authorization, domain graph hydration, persistence lookup, and command application remain
application/adapter responsibilities. Both majors share the neutral contracts of
[jsonapi-java-jackson-api](../jsonapi-java-jackson-api/README.md) per ADR-007.

## Further reading

- [Architecture overview](../docs/architecture.md)
- [Conformance checklist](../docs/conformance.md)
- [ADR-016 — Mapper-instance construction for Jackson adapters](../docs/adr/016-jackson-adapter-construction.md)
- [ADR-010 — Architectural tests](../docs/adr/010-architectural-tests.md)
- [Canonical fixtures](../jsonapi-java-jackson-api/src/testFixtures/resources/jsonapi/corpus/1.1/README.md)
- [Jackson API module](../jsonapi-java-jackson-api/README.md)
- [Jackson 3 module](../jsonapi-java-jackson3/README.md)
- [Root agent workflow](../AGENTS.md)

## For contributors / agents

- **Validate then write / read then validate:** `JsonApiDocumentWriter` and
  `JsonApiDocumentReader` are the sole public codec paths. Failures preserve stable diagnostics
  (`ValidationRuleCode` + JSON Pointer-like path; reads also carry `CodecFailureCategory` and safe
  source location). Emission failures propagate Jackson 2 checked `IOException` mechanics rather
  than a new exception family, and every reader overload declares `IOException` while Jackson parse
  failures become payload-safe `MALFORMED_JSON` values. Do not expose the codec mapper publicly.
- **Mapper isolation:** factories accept configured `JsonMapper` instances, never builders, and
  do not mutate them. Close only generators created by convenience overloads; leave caller-owned
  streams/writers/generators open.
- **Architectural tests:** `Jackson2DependencyRulesSpec` allows JDK, JSpecify, core public
  packages, annotations, the common contracts package, module-owned types, and
  `com.fasterxml.jackson..`; bans `core.internal` and Jackson 3 (`tools.jackson..`) in
  production sources, and asserts no moved common-contract type is re-declared here (ADR-010).
- **Tests:** Spock specs under `src/test/groovy/` are boring and explicit: setup, invoke the
  production API, assert directly. Shared test fixtures provide passive corpus/schema resources
  via `jsonapi-java-jackson-api` test fixtures; writer expectations belong in adapter tests. Do
  not introduce shared test orchestration, scenario registries, or assertion frameworks. Small
  duplication between the Jackson 2 and Jackson 3 test suites is acceptable.
- **Nullness:** Production packages are `@NullMarked` (JSpecify only). Use `@Nullable` for
  absence and intentionally null map values. Do not import `core.internal`.
