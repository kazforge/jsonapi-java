# Documentation

This repository keeps user-facing documentation separate from repository-maintenance material.

| Surface | Owns |
|---------|------|
| [`site/`](site/) | Public `jsonapi-java` documentation source, built by MkDocs and published through GitHub Pages |
| [`architecture.md`](architecture.md) | Current cross-module composition and authority boundaries |
| [`conformance.md`](conformance.md) | Current JSON:API support status by feature and layer |
| [`vision.md`](vision.md) | Stable product direction |
| [`adr/`](adr/README.md) | Consequential architectural rationale |
| [`../AGENTS.md`](../AGENTS.md) and [`.agents/skills/`](../.agents/skills/) | Repository workflow, task routing, and completion gates |
| Javadoc | Public API contracts and semantics |

Do not add maintainer docs to `site/` merely because MkDocs can render Markdown. Public pages should
answer a concrete library-user question; repository guidance remains in its canonical document.

## Preview and build

Run from the repository root on Linux x86_64 with Python 3.14 and its `venv` module installed.
Windows contributors use WSL2 on x86_64. The locked hashes in `requirements.txt` currently
target Linux x86_64 CPython 3.13 and 3.14 for CI and local preview; other platforms, architectures,
or Python versions are not covered by the lock:

```bash
python3 -m venv .venv-docs
. .venv-docs/bin/activate
python -m pip install --only-binary :all: --require-hashes --requirement docs/requirements.txt
mkdocs serve --strict
```

Open <http://127.0.0.1:8000/jsonapi-java/>. Build the deployable site without starting a server with:

```bash
mkdocs build --strict
```

Generated output is `build/docs-site/` and is not committed.

## Edit a public page

1. Add or edit Markdown and assets below `docs/site/`.
2. Add the page to the deliberately shallow `nav` in [`../mkdocs.yml`](../mkdocs.yml).
3. Use relative links between site pages and run `mkdocs build --strict` before sending the change.

The GitHub Actions workflow validates the same build for relevant pull requests and deploys only from
`main` through the GitHub Pages artifact flow.
