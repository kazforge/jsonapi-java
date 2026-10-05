# Security policy

Maintainers own vulnerability triage and merge protection. Dependabot Alerts provide the dependency
vulnerability signal; Renovate is the sole dependency-update and security-fix PR bot. CodeQL
provides code vulnerability findings. Inspect GitHub's Security tab and native Actions results
when assessing these signals.

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
