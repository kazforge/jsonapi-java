# Release lifecycle

One coherent release lifecycle for the unified release train. Version and
change semantics are owned by
[ADR-021](adr/021-unified-release-train.md); this page owns the mechanics that
implement it.

```
Conventional Commits
        ↓
release-please
        ↓
Release PR
- gradle.properties version
- CHANGELOG.md
        ↓ merge
Git tag v<version> + GitHub Release
        ↓
GitHub Actions
        ↓
Gradle build / sign / Maven Central publish
```

## Single root release

- release-please is the only release and version orchestrator. There is no
  Gradle-driven version calculator and no Nebula Release plugin.
- One release-please package at the repository root (`.`).
- One version for the complete repository release, stored once in the root
  `gradle.properties` `version` property and consumed by all modules.
- All six `com.kazforge:jsonapi-java-*` artifacts always ship that exact
  version: `jsonapi-java-core`, `jsonapi-java-annotations`,
  `jsonapi-java-jackson-api`, `jsonapi-java-query`, `jsonapi-java-jackson2`,
  `jsonapi-java-jackson3`.
- Tags are named `v<version>` (for example `v0.2.0`); the component name is not
  included in the tag.
- The changelog is the root `CHANGELOG.md`, maintained by release-please.
- Configuration lives in `release-please-config.json` with version tracking in
  `.release-please-manifest.json`.

## Release classification

The release-please configuration implements the ADR-021 bump rules without
custom commit parsing:

| Commit change | Release effect |
|---------------|----------------|
| `feat` | Minor release |
| `fix` or `perf` | Patch release |
| `refactor`, `docs`, `test`, `build`, `ci`, `chore`, and similar internal-only changes | No release by default |
| `!` in the commit header or a `BREAKING CHANGE:` footer before 1.0 | Minor release, never patch |
| `!` in the commit header or a `BREAKING CHANGE:` footer from 1.0 onward | Major release |

Relevant configuration:

- `bump-minor-pre-major: true` keeps pre-1.0 breaking changes on minor.
- `bump-patch-for-minor-pre-major: false` keeps pre-1.0 features on minor.
- `skip-snapshot: true` disables the Java strategy snapshot pull requests, so
  there is no automatic snapshot bump mechanism competing with the release PR.
- `include-component-in-tag: false` with `include-v-in-tag: true` produces
  `v<version>` tags.
- `gradle.properties` carries the release-please version annotations, so the
  release PR updates the single version source directly.

## What is out of scope

- Independent per-artifact trains or version variance.
- A BOM artifact.
- Spring artifacts.
- Automated snapshot publication.
- Release-candidate or other pre-release automation.
- API or dependency compatibility tooling and SBOM or provenance generation,
  which extend the publication lifecycle without duplicating orchestration.

## Normal development

- Write Conventional Commits on the path to `main`. The Conventional Commits
  check guards PR titles and commit messages.
- Do not edit the version by hand to cut a release. Do not publish mutable
  snapshots from development branches.
- Between releases the repository version stays at the last release version
  until the next release PR proposes the bump.

## Maintainer runbook

1. Let release-please open and update the release PR on `main`. Review the
   proposed version in `gradle.properties`, the `CHANGELOG.md` entry, and the
   manifest update together.
2. Merge the release PR when the notes and version are correct. Merging is the
   release decision.
3. release-please creates the `v<version>` tag and the corresponding GitHub
   Release from the merged PR.
4. The `Publish` workflow starts from the published release. It runs
   `./gradlew clean build publish`, assembles `build/central-bundle.zip` from
   `build/staging-deploy`, and uploads the bundle to the Central Portal with
   automatic publishing.
5. Verify the deployment in the Central Portal and the artifacts under the
   `com.kazforge` namespace. The published release is immutable; fixes ship as
   the next release train version.

## Publication

Gradle owns Java publication with standard `maven-publish` and `signing`:

- Each publishable module applies the shared `jsonapi-java-publish`
  convention, which configures the `mavenJava` publication from the `java`
  component with the module `artifactId` and the root project version.
- Every artifact publishes the main JAR plus sources and Javadoc JARs. The
  shared library conventions already assemble Javadoc JARs; the publish
  convention additionally enables sources JARs.
- Every publication carries the required POM metadata: name, description, project
  URL, Apache-2.0 license, developer, and SCM coordinates.
- The publish convention stages all six publications into the shared
  `build/staging-deploy` Maven layout. The `Publish` workflow zips that layout
  and uploads it to the Central Portal Publisher API. The workflow never
  calculates a second version; it publishes the release-please-owned version.
- The `com.kazforge` Sonatype Central namespace is already verified. The
  superseded `io.github.kazemek` coordinates are not published.

## Credentials and signing setup

All secrets stay out of the repository. Required GitHub Actions secrets:

| Secret | Source |
|--------|--------|
| `RELEASE_PLEASE_TOKEN` | Fine-grained personal access token for the release-please workflow |
| `CENTRAL_USERNAME` | Central Portal user token username |
| `CENTRAL_PASSWORD` | Central Portal user token password |
| `SIGNING_KEY` | ASCII-armored GPG private key used for artifact signing |
| `SIGNING_PASSWORD` | Passphrase for `SIGNING_KEY`, empty when the key has none |

Setup:

1. Create a fine-grained personal access token scoped to this repository with
   Contents read/write and Pull requests read/write access. Store it as
   `RELEASE_PLEASE_TOKEN`. The release-please workflow must run with this
   token rather than the default `GITHUB_TOKEN`: releases created with
   `GITHUB_TOKEN` do not trigger downstream workflows, so the `Publish`
   workflow would never fire.
2. Create a Central Portal user token from the Portal account settings. Store
   the generated username and password as `CENTRAL_USERNAME` and
   `CENTRAL_PASSWORD`. Portal tokens differ from the Portal login password.
3. Generate a dedicated signing key pair for releases and distribute the public
   key to a public keyserver so consumers can verify signatures.
4. Export the private key armored and store it as `SIGNING_KEY`; store its
   passphrase as `SIGNING_PASSWORD`.
5. Gradle reads the signing material through the `signingKey` and
   `signingPassword` project properties, mapped in the workflow from
   `ORG_GRADLE_PROJECT_signingKey` and `ORG_GRADLE_PROJECT_signingPassword`.
   Local builds without those properties skip signing so `./gradlew clean build`
   stays token-free; release builds with them sign every publication.

## Failure and retry behavior

- Release PR out of date or conflicting: let release-please update the PR,
  resolve any `gradle.properties`, `CHANGELOG.md`, or manifest conflicts in
  favor of the release-please proposal, and re-review the version.
- Missing or expired `RELEASE_PLEASE_TOKEN`: the `Release Please` workflow run
  fails visibly and the chain stalls before any release PR. Renew the token
  with the same repository scopes, update the secret, and re-run the workflow.
  Do not fall back to the default `GITHUB_TOKEN`; releases it creates would not
  trigger the `Publish` workflow.
- Missing tag or GitHub Release after merging: re-run the `Release Please`
  workflow for the `main` branch. Do not create the tag by hand; the tag must
  match the manifest version and the release PR.
- `Publish` workflow build or signing failure: fix the underlying build or
  signing secret, then re-run the failed workflow run. The release tag already
  exists, so a re-run republishes the same version.
- Central Portal validation failure: inspect the deployment in the Portal UI,
  fix POM metadata, sources, Javadoc, or signing, cut the fix as the next
  release train version, and publish again. Published releases are never
  overwritten.
- The `central-bundle` workflow artifact (bundle zip plus staging layout) is
  retained for inspection. A maintainer with Portal access can also upload that
  bundle manually through the Portal UI when automation cannot proceed.
