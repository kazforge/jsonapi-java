# Security scanning

Maintainers own vulnerability triage and merge protection. Dependabot alerts are the dependency
vulnerability signal; Renovate is the sole dependency-update and security-fix PR bot. Jackson
minimum versions remain maintainer-controlled. Current Jackson compatibility tests are separate
from the default dependency graph and are not a second submitted graph.

## Scans and triage

[`Generate dependency graph`](../.github/workflows/dependency-graph.yml) resolves the default Gradle
graph for all seven active modules and included `build-logic`, without project/configuration
filters. It runs on PRs to `main`, main pushes, weekly refreshes, and manual dispatch with read-only
permissions and checksum verification enabled. The native PR check is **Dependency graph**.
[`Submit dependency graph`](../.github/workflows/dependency-submission.yml) uses the official Gradle
artifact download/submission action after successful generation. This trusted `workflow_run`
consumer has write permission but never checks out or executes PR code, including fork PR code.

[`Dependency Review`](../.github/workflows/dependency-review.yml) runs separately on every PR to
`main`. Its native **Dependency review** check blocks introduced High/Critical vulnerabilities in
runtime, development, and unknown scopes. License checks, Scorecard output, and PR comments are
disabled. It retries snapshot warnings for up to 600 seconds while submission completes.

[`CodeQL`](../.github/workflows/codeql.yml) uses advanced setup for `java-kotlin` and the default
security suite on main PRs/pushes, weekly scans, and manual dispatch. It compiles production classes
with JDK 21, checksum verification, and build/configuration caches disabled; it does not run tests
or Sonar. Existing CI, Sonar, and release checks remain unchanged.

Inspect Actions job logs/summaries for generation, submission, and review results; inspect GitHub's
Security tab for Dependabot and code-scanning alerts. For dependency provenance, use Gradle
`dependencyInsight` with the affected project/configuration, or `buildEnvironment` for plugin
classpaths. Maintainers assess applicability and choose a fix or a documented alert dismissal.
High/Critical findings block according to the dependency check and CodeQL rule below; triage lower
severities without adding a merge block. Preserve Jackson compatibility baselines when selecting
fixes rather than silently raising minimum versions.

These are stock tools, not a custom fail-closed missing-data gate: the graph plugin is not a strict
resolution-completeness validator, and Dependency Review can proceed with snapshot warnings after
its retry timeout. Investigate native generation/submission failures and warnings operationally;
a successful review alone does not prove that every expected snapshot was available.

## GitHub settings and bootstrap

The workflows do not configure repository settings. Maintainers must:

1. Enable **Dependency graph** and **Dependabot alerts**. Leave **Dependabot security updates**
   disabled, and confirm Renovate has **Dependabot alerts: read** access.
2. Use CodeQL **advanced setup**, not duplicate default setup.
3. Merge the trusted submission workflow onto `main` before relying on `workflow_run`. Bootstrap
   the default graph by running **Generate dependency graph** on `main`; inspect its successful
   submission and the repository dependency graph. Verify normal and fork PR submission/review,
   native check names, and CodeQL compilation/upload in representative GitHub runs.
4. After bootstrap, require **Dependency graph** and **Dependency review** on `main`, preserving
   every existing required check. Do not require the trusted consumer's default-branch status on
   PRs.
5. Add a main ruleset **Require code scanning results** for **CodeQL**, with **Security alerts:
   High or higher** and **Alerts: None**. Do not also require CodeQL's workflow status unless
   observed GitHub behavior demonstrates a need.

The trusted submission workflow and real GitHub behavior cannot be fully validated locally. Check
these settings and representative runs before enabling the new protections.
