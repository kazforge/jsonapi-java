# Contributing

This document covers the commit and pull-request grammar enforced in CI. For build and repository
policy, see the root [README](README.md); for release, version-bump, and breaking-change semantics,
see [ADR-021](docs/adr/021-unified-release-train.md).

## Conventional Commit grammar

Both **pull-request titles** and **individual commit messages** must follow the project Conventional
Commit grammar:

```
type[(scope)]: <description>
```

- Allowed types: `feat`, `fix`, `perf`, `refactor`, `docs`, `test`, `build`, `ci`, `chore`,
  `revert` (lowercase, no other types).
- Scopes are optional and unrestricted — any scope value is accepted (lowercase).
- The description after `:` must be non-empty.

Valid examples:

- `feat(reader): add option`
- `feat!: breaking change`
- `fix(core): correct UTF-16 handling`
- `revert: fix(core): restore behavior`

Invalid examples:

- `update stuff` — no type
- `Docs: add MkDocs site infrastructure` — type must be lowercase from the allowlist
- `feat(reader)!:` — empty description
- `feat(reader): Correct behavior` — description must not start uppercase

## Breaking changes

PR titles have no footer, so a breaking pull request must mark it in the title header with `!`:

- `feat!: breaking change`
- `feat(reader)!: breaking change`

Commit messages may mark a breaking change with `!` in the header **and/or** a well-formed
`BREAKING CHANGE:` footer (the `BREAKING-CHANGE:` alias is accepted):

```
feat(reader)!: drop deprecated option
```
```
feat(reader): drop deprecated option

BREAKING CHANGE: the deprecated option was removed
```

The breaking-change footer token must be exact-case — `BREAKING CHANGE:` or `BREAKING-CHANGE:`,
colon, and a non-empty explanation. Lowercase variants (`breaking change: ...`) or the token without
a colon fail. The explanation may continue on following footer lines.

Which semantic bump a `!` or breaking-change marker corresponds to (and every other versioning
question) is owned by [ADR-021](docs/adr/021-unified-release-train.md); this grammar only decides
whether the marker is present and well-formed.

## Why both titles and commit messages are checked

The repository preserves both GitHub merge options, and each consumes different text:

- **Squash merge** uses the pull-request title as the squash-commit header.
- **Rebase merge** keeps the individual commit messages and rewrites only the committer rows.

Checking both means either merge option produces a valid history. The guardrail does not force a
merge strategy.

## CI enforcement

CI validates pull requests with the `Conventional Commits` check (`.github/workflows/conventional-commits.yml`).
It lints the PR title and every commit message reachable from the PR head that is not already on
`main`, so merge-base bounded ranges are re-linted only once. Commitlint's default ignores still
apply to the commit range, so git-generated `Merge ...` and `Revert "..."` messages are skipped; a
hand-written `revert: ...` commit is validated and accepted.

To make the check mandatory on `main`, add the `Conventional Commits` status check to the required
checks for `main` in GitHub branch protection (or the matching repository ruleset). Applying repo
settings requires admin rights and is a maintainer action outside code changes. Note that direct
pushes to `main` bypass pull-request guardrails; a repository ruleset that blocks direct pushes is
optional separate hardening.

There are no local git hooks; CI is the enforcement point. You can still verify locally with
commitlint (from `.github/commitlint/`, requires Node):

```sh
# Lint a PR title draft (the title read disables commitlint's default
# ignore prefixes, so git-generated merge/revert forms fail like any other
# invalid title)
printf 'feat(reader): add option\n' | npx commitlint --config commitlint.title.mjs --verbose

# Lint a commit range, as CI does (default ignore prefixes apply)
npx commitlint --from <merge-base> --to <head> --verbose
```

Adding a new commit type later is a grammar change; it requires an explicit policy update to the
allowlist here, in the commitlint config, and to any release policy that maps types to bump rules.
