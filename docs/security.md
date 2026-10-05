# Security policy

Maintainers own vulnerability triage and merge protection. Dependabot Alerts provide the dependency
vulnerability signal; Renovate is the sole dependency-update and security-fix PR bot. CodeQL
provides code vulnerability findings. Inspect GitHub's Security tab and native Actions results
when assessing these signals.

Renovate execution is owned by KazForge and runs on GitHub-hosted Actions runners
using a dedicated GitHub App with repository-scoped, short-lived installation tokens.
`renovate.json` remains the dependency-update policy source. The runner permits the
Gradle wrapper to generate verification metadata as part of dependency updates;
normal builds and CI continue to enforce dependency verification. This execution
trust is limited to `kazforge/jsonapi-java` and its maintainer-controlled build code.

Dependency Review blocks introduced High/Critical dependency vulnerabilities. High/Critical
CodeQL security findings block merging through code-scanning protection. Maintainers triage
Moderate/Low findings without an automatic merge block.

For applicable findings, maintainers choose and verify a fix. If a finding is inapplicable or a
remediation is not warranted, maintainers document the rationale when dismissing the alert.
Jackson minimum versions remain explicit maintainer compatibility decisions; security fixes must
not silently raise them.

Dependency graph generation and review use stock GitHub/Gradle capabilities and do not claim strict
resolution or snapshot completeness. Investigate native failures and snapshot warnings; a
successful review alone does not establish complete dependency coverage.
