# Contributing

Install Git and a local JDK 21. Fork and clone the repository, then create a branch for your change.
Use the committed Gradle wrapper; no separate Gradle installation is needed.

For source or build changes, run from the checkout root:

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew clean build
```

These format, compile, test, and enforce the [build checks](README.md#build).
For Markdown-only changes, check links and consistency; no Gradle build is required.
See [completion gates](AGENTS.md#completion-gates) for other change types.
All applicable CI checks must pass before merge; Sonar analysis runs in CI.

Open a PR against `main` for one coherent change, with focused tests where applicable and a short
description of the change and checks run. Use the [module map](README.md#modules) and applicable
[ADRs](docs/adr/README.md) to keep scope clear; leave unrelated cleanup out.

Use [Conventional Commits](docs/adr/014-unified-release-train.md#conventional-commits--version-bumps)
for commit messages and PR titles.
