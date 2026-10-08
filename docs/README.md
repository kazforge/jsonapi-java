# Documentation

This repository keeps user-facing documentation separate from repository-maintenance material.

| Surface | Owns |
|---------|------|
| [`site/`](site/) | Canonical [user guide](https://jsonapi.kazforge.com/), built by MkDocs and published through GitHub Pages |
| [`architecture.md`](architecture.md) | Current cross-module composition and authority boundaries |
| [`conformance.md`](conformance.md) | Current JSON:API support status by feature and layer |
| [`release.md`](release.md) | Release/publication mechanics and dependency-baseline verification |
| [`security.md`](security.md) | Repository security signals, ownership, and vulnerability triage policy |
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

Open <http://127.0.0.1:8000/>. Build the deployable site without starting a server with:

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

## Search discovery and indexing

Public page titles and descriptions live in each page's YAML front matter under `site/`; the global
fallback description and canonical `site_url` live in [`../mkdocs.yml`](../mkdocs.yml). Keep navigation
labels short and preserve existing heading fragments when changing headings. GitHub's repository
description and topics are separate repository settings; keep them aligned with shipped capabilities
and keep the website URL set to `https://jsonapi.kazforge.com/`.

After a strict build, inspect the generated HTML for a useful title, one page-specific description,
and an exact HTTPS custom-domain canonical. MkDocs generates `sitemap.xml` natively; it should list
the eight public guide pages, not `404.html` or maintainer documents. Only `site/` is published.
[`site/robots.txt`](site/robots.txt) advertises the canonical sitemap without blocking pages or assets.

After normal deployment, check that the public pages, `/sitemap.xml`, and `/robots.txt` return 200,
with no restrictive robots meta tag or `X-Robots-Tag` header. Recheck permanent redirects from
`http://jsonapi.kazforge.com/` and `https://kazforge.github.io/jsonapi-java/` to the HTTPS custom domain;
direct `index.html` URLs should declare their directory URL as canonical. See Google's
[robots.txt reference](https://developers.google.com/crawling/docs/robots-txt/robots-txt-spec) for crawler behavior.

Search Console setup and monitoring are maintainer-run, not part of the site build:

1. Reuse an existing verified property, or add a Domain property for `jsonapi.kazforge.com` and
   complete [DNS ownership verification](https://support.google.com/webmasters/answer/9008080?hl=en).
   Keep verification records in DNS; do not add tokens, analytics, or verification files to the site.
2. [Submit the sitemap](https://developers.google.com/search/docs/crawling-indexing/sitemaps/build-sitemap)
   at `https://jsonapi.kazforge.com/sitemap.xml` in Search Console.
3. Use [URL Inspection](https://support.google.com/webmasters/answer/9012289?hl=en) for the homepage and
   major task pages to check Google's selected canonical and live crawlability; request indexing where useful.
4. Monitor the [Page indexing report](https://support.google.com/webmasters/answer/7440203?hl=en) for
   exclusions and the [Performance report](https://support.google.com/webmasters/answer/7576553?hl=en)
   for search queries, impressions, and clicks after deployment and subsequent content changes.

Crawlability and sitemap submission do not guarantee indexing or ranking.
