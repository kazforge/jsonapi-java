# Build And Verification

- Install JDK 21 locally; the Gradle toolchain requires it, and no resolver is configured to
  download it. Use the committed wrapper.
- Primary token-free verification: `./gradlew clean build`. It compiles, runs Spock/JUnit and
  ArchUnit tests, produces JaCoCo reports, enforces the fixed 80% JaCoCo line/branch floor
  through `check` (numbers only in `build-logic/.../jsonapi-java-library.gradle.kts`; policy in the
  root README Build section), and runs `spotlessCheck` through `check`.
- Coverage thresholds are repository policy, not measured-value ratchets. Do not raise or lower the
  fixed coverage floor to match current coverage or make CI pass. Address failures with meaningful
  tests unless the user explicitly authorizes a policy change.
- Run one spec with `./gradlew :<module>:test --tests '<spec FQCN>'`, for example
  `./gradlew :jsonapi-java-core:test --tests 'com.kazforge.jsonapi.core.validation.UpdateRequestValidationSpec'`.
  Ordinary incremental and scoped test execution is the default once task inputs are correct;
  do not use `--rerun-tasks` as routine workflow.
- `clean` alone does not prove tests re-executed rather than being restored from the build
  cache. `--no-build-cache` is an exceptional troubleshooting or final-verification path: it
  disables build-cache reuse only and does not disable ordinary up-to-date checking. For a
  deliberately cache-free full verification, use `./gradlew clean build --no-build-cache`.
- Dependency verification is checksum-enforced by `gradle/verification-metadata.xml`. After a
  dependency change or verification failure, run
  `./gradlew --refresh-dependencies --write-verification-metadata sha256 clean build`; never disable
  verification globally. Preserve the IntelliJ Gradle-sync exceptions when regenerating: the
  `*-sources.jar` / `*-javadoc.jar` trusted-artifact classifier rules, the `gradle:gradle`
  `-src.zip` checksum (refresh it when bumping the wrapper), and the Groovy 4.0.32 DSL-parser
  `.module` / `.pom` checksums (refresh that pin if the wrapper's bundled Groovy changes). Do not
  widen those exceptions to group-level trusts or `verify-metadata=false`.

# Repository Shape

- `settings.gradle.kts` is the only source of truth for present modules; the root README also lists
  planned modules, which have no usable entry points. Read the affected module README before code.
- Sources are under `<module>/src/main/`; Spock specs are under `src/test/groovy/`, with some Java
  test fixtures under `src/test/java/`.
- `build-logic/` owns shared Java/test/nullness/coverage and Spotless configuration. Dependency
  versions are in `gradle/libs.versions.toml`; there is no code-generation step.
- Shared test fixtures contain major-neutral application-shaped DTOs, canonical JSON/schema
  resources, and the neutral `TestFixtureResources` loader under
  `jsonapi-java-jackson-api/src/testFixtures`. Fixture behavior is limited to the minimum needed to
  represent or observe the application shape under test; behavioral expectations and assertions
  belong in each adapter's own tests. Do not introduce shared test orchestration, expected-outcome
  descriptors, scenario registries, or assertion frameworks.
- Before changing shared fixtures or corpora, read the affected corpus/schema resource READMEs; those files own fixture-specific invariants.
- Keep orthogonal concerns orthogonal: test semantic behavior through one representative entry
  point, and test overload/sink parity with representative data; do not cross-product both
  dimensions, and prefer explicit table rows and direct assertions over case catalogs,
  nullable expectation parameters, or assertion interpreters.

# Task Routing

Choose the narrowest affected module or root subsystem first. Expand only for callers,
dependencies, public API effects, or repository-wide configuration.

Skills live at `.agents/skills/<name>/SKILL.md`. Lifecycle workflow entrypoints are entered only
through explicit maintainer request or authorization; workflow-support skills execute as part of an
already-authorized workflow per that workflow's documented contract. The routing tables below are
descriptive, not an automatic task-matching router:

**Lifecycle workflow entrypoints** (entering one changes or establishes lifecycle/authorization context):

| User intent | Lifecycle workflow entrypoint |
|-------------|-------------------------------|
| Plan one coherent implementation increment | `implementation-planning` |
| Request an independent design challenge | `implementation-design-review` |
| Request an independent plan review | `implementation-plan-review` |
| Implement requested work | `implement-work` |

**Workflow-support skills** (may execute within an authorized workflow; may also be requested directly):

| Trigger / purpose | Workflow-support skill |
|-------------------|------------------------|
| Review implementation against the requested outcome | `implementation-review` |
| Produce a fresh-session review handoff when required | `implementation-handoff` |
| Add a module or change public packages, entry points, validate/read flows, or non-goals | `module-docs` |
| Format Spotless-covered files | `spotless-format` |

**Planning uses the capabilities available in the current session.** Establish facts from repository
evidence and primary sources. When executable tools are available, bounded planning spikes may be
used when they materially reduce uncertainty. If an important planning action cannot be performed
with the available capabilities, surface the limitation to the maintainer. The repository does not
depend on, or prescribe, any particular harness mode.

Informal planning and exploration may happen in any session using the capabilities available there.
The `implementation-planning` workflow is a defined engineering workflow: it requires the
capabilities necessary to execute reliably and must not silently degrade into a partial version when
those capabilities are unavailable.

- A lifecycle workflow entrypoint must not be entered merely because the current task appears to
  match it.
- Top-level lifecycle workflow entrypoints require explicit maintainer request or authorization,
  which the harness may realize semantically rather than via literal slash syntax. An authorized
  workflow may execute, delegate to, or load workflow-support instructions, or execute an explicitly
  authorized nested lifecycle workflow, according to its documented contract and the capabilities
  available in the current session. Using a workflow-support skill never grants authority to enter a
  new lifecycle workflow or to transition from planning to implementation.
- If requested implementation work is not one coherent increment against current repository reality,
  stop with **Needs decomposition** and return control to the user/coordinating layer. Do not invent
  a repository multi-plan DAG.
- A direct user/maintainer request or authorization to enter a lifecycle workflow is sufficient
  authority for that workflow. No external tracker is required. `implement-work` remains the single
  implementation authorization boundary.
- Planning is never automatic: planning ends ready for implementation, and implementation begins
  only when the maintainer explicitly requests or authorizes the `implement-work` lifecycle workflow.
  There is no separate mandatory `Build now?` confirmation; explicit maintainer intent is the
  authorization.
- Design Review and Plan Review are optional, advisory, and maintainer-controlled. Neither grants
  implementation permission. An authorized workflow may execute an approved review directly rather
  than requiring another explicit instruction.
- Read `settings.gradle.kts`, the module README, changed-package `package-info.java`, exact sources
  and mirrored tests, then only directly relevant ADRs or conformance sections. Read
  `docs/architecture.md` when the work is cross-module.
- Read `docs/vision.md` only for new modules, product-boundary changes, or stable direction.

# Architecture Constraints

- This library represents and validates JSON:API documents. Persistence, endpoints, authorization,
  and query execution remain application policy; do not hide policy in mapping or adapter defaults.
- `jsonapi-java-core` has no functional third-party runtime dependencies; compile-only JSpecify is
  allowed. Optional integrations belong in separate modules.
- `jsonapi-java-jackson-api` must remain free of Jackson-major imports
  (`tools.jackson.*` and `com.fasterxml.jackson.*`) despite its name.
- Preserve wire-visible distinctions: absent, explicit JSON `null`, and present-empty are different
  states. Explicit null data/linkage uses sealed model variants, not bare Java null.
- Production Java packages are JSpecify `@NullMarked`; NullAway checks `compileJava` only. ArchUnit
  enforces module dependency allowlists. Do not weaken those rules without updating
  `docs/adr/010-architectural-tests.md`.

# Test Design

Adapter tests optimize for locality and independent behavioral proof rather than production-style
DRYness. Small duplication is preferable to indirection that hides which API is exercised or what
behavior is expected.

- A parameterized test should normally exercise one production entry point and one assertion shape.
  Do not route rows through `kind`, `operation`, `source`, or expected-outcome tags that choose the
  production call or verification strategy. Split those cases into separate tests instead.
- Prefer direct value equality and explicit assertions. Do not build generic recursive comparators,
  semantic verifiers, or assertion DSLs to accommodate exceptional cases; isolate exceptional
  ordering, array, or representation semantics in focused tests with focused assertions.
- Small spec-local helpers are appropriate when they only construct repeated input or express one
  narrow assertion. If understanding a test requires following a registry, dispatcher, verifier, or
  several helper layers, simplify the test.
- Shared `testFixtures` may provide major-neutral input data and application-shaped types, including
  minimal observable behavior such as access counters when the behavior under test requires it.
  They must not invoke adapter APIs, select scenarios, encode expected behavioral outcomes, or
  contain assertions. Jackson-major-specific mechanism fixtures remain adapter-local.
- Shared JSON/schema corpora are test input and inventory, not a behavioral oracle. Adapter-specific
  diagnostics, locations, policies, and expected decoded/mapped values belong in adapter-owned
  specifications.
- Branches inside test doubles, custom serializers/deserializers, concurrency probes, or other code
  that models a genuinely branching production collaborator are fine. The smell is control flow
  whose purpose is deciding which test operation or assertion semantics a row represents.

# Knowledge And Plans

Every durable fact has one canonical owner; other documents should link or provide only navigation.

| Fact | Canonical owner |
|------|-----------------|
| Current module capability and usage | `<module>/README.md` |
| Current cross-module architecture mental model | `docs/architecture.md` |
| Human-readable module inventory | root `README.md` |
| Actual build membership | `settings.gradle.kts` |
| Package responsibility and invariants | `package-info.java` |
| Public API contract and semantics | Javadoc |
| Behavioral proof | tests |
| Cross-cutting architecture rationale | accepted ADR under `docs/adr/` |
| JSON:API compliance state | `docs/conformance.md` |
| Workflow and agent routing | this file and `.agents/skills/` |
| Planning/review finding severity and stage ownership | `.agents/skills/review-findings.md` |
| Stable product direction | `docs/vision.md` |
| Requested work intent / acceptance intent | Explicit user/maintainer request, or external coordinating layer when present |
| Backlog, prioritization, decomposition, blockers | External coordinating layer when present |
| Temporary engineer/agent planning memory | gitignored `.agentWork/plans/*.md` and `.agentWork/.session/spikes/` (not engineering truth) |
| Temporary review/session context | gitignored `.agentWork/.session/` |
| Forensic history | Git |

- Snapshot (current repository evidence) and Vision are separate authorities. Surface conflicts;
  never change implementation merely to make it match Vision.
- External coordinating-layer metadata is optional coordination and traceability, not engineering
  truth or a correctness gate. When a coordinator is unavailable, never invent backlog order from
  local plans, source layout, or Git history; report coordination as unsynchronized if asked to
  sync.
- Local working plans are optional, gitignored, and disposable. They are not backlog entries,
  roadmaps, dependency graphs, or permanent architecture docs. Delete them when no longer useful.
  Do not create a plans index or archive.
- Implementation Review evaluates requested outcome / acceptance intent + actual diff + repository
  contracts/docs/tests + applicable gates. It must not reconstruct the requested work from a local
  plan, branch names, a tracker, Git history, or inferred code alone.
- Committed workflow skills are tracker-agnostic. Tracker-specific conventions belong only in
  maintainer-local coordinating skills outside the public workflow.

# Completion Gates

Classify the final diff; tiers combine when multiple scopes are touched.

| Changed paths | Required gates |
|---------------|----------------|
| Docs/planning only (`**/*.md`, `docs/**`, `.agentWork/**`) | Review links, consistency, and section order; no build |
| Workflow only (`.github/**`, `.editorconfig`, `.gitattributes`, `.gitignore`) | No local gate; CI validates workflow behavior |
| Build configuration (`**/*.gradle.kts`, `gradle/**`, `build-logic/**`, `config/**`) | `./gradlew clean build`; also Spotless if a covered file/config changed |
| Module production/test sources (`jsonapi-java-*/src/**`) | `spotless-format` -> `./gradlew clean build` |
| Other/unclassified paths, including `fixtures/**` | Classify explicitly; otherwise use the full source tier |

- For covered `.java`, `.groovy`, `.kt`, or `.gradle.kts` changes, run `./gradlew spotlessApply`
  then `./gradlew spotlessCheck` before the build.
- Source completion is the repository's deterministic local gates: Spotless, compilation, tests,
  JaCoCo floors, ArchUnit, NullAway, and the other repository-local `check` work that `./gradlew
  clean build` already runs. Do not wait on a local SonarCloud analysis, Quality Gate wait, or
  Issues API round-trip before opening a PR. Ordinary `./gradlew clean build` stays token-free; do
  not attach `sonar` to `build`/`check`.
- CI remains the authority for Sonar. On `main` pushes and PRs, CI runs
  `./gradlew clean spotlessCheck build sonar` (Quality Gate wait) then
  `.github/scripts/check-new-code-issues.sh --list` so neither agents nor CI can complete on Quality
  Gate alone. The script fails closed by default; do not treat a green Quality Gate or a printed
  non-zero `total` as success. Failed-run details are in the `gradle-reports` artifact and the
  Unit tests check.
