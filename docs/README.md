# Documentation

This repository keeps user-facing documentation separate from repository-maintenance material.

| Surface | Owns |
|---------|------|
| [`site/`](site/) | Core-owned source for the [user guide](https://jsonapi.kazforge.com/) |
| [`architecture.md`](architecture.md) | Current cross-module composition and authority boundaries |
| [`conformance.md`](conformance.md) | Current JSON:API support status by feature and layer |
| [`release.md`](release.md) | Release/publication mechanics and dependency-baseline verification |
| [`security.md`](security.md) | Repository security signals, ownership, and vulnerability triage policy |
| [`vision.md`](vision.md) | Stable product direction |
| [`adr/`](adr/README.md) | Consequential architectural rationale |
| [`../CONTRIBUTING.md`](../CONTRIBUTING.md) | Contributor setup, verification commands, and dependency-verification maintenance |
| [`../AGENTS.md`](../AGENTS.md) | Optional repository onboarding for agents |
| Javadoc | Public API contracts and semantics |

Do not add maintainer docs to `site/` merely because MkDocs can render Markdown. Public pages should
answer a concrete library-user question; repository guidance remains in its canonical document.

## Publishing

Core owns the guide source and navigation under `docs/site/`.
[`jsonapi-docs`](https://github.com/kazforge/jsonapi-docs) owns shared MkDocs assembly,
validation, and GitHub Pages deployment, including portal-root discovery files.
See its README for preview and build instructions.

Core publishes immutable release documentation snapshots and append-only documentation revisions
through the shared workflow. The [release lifecycle](release.md) owns publication mechanics;
Core does not build or deploy a standalone site.

## Edit a public page

1. Add or edit Markdown and assets below `docs/site/`.
2. Add the page to [`site/nav.yml`](site/nav.yml), keeping `index.md` first.
3. Use relative links between site pages and check links and assets under `/core/<version>/`.
