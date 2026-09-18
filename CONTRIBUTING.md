# Contributing

Pull-request titles and commit messages must follow
[Conventional Commits](https://www.conventionalcommits.org/):

```
<type>[(scope)]: <description>
```

Examples: `feat: add option`, `feat(reader): add option`, `fix(core): correct
handling`, `feat!: drop deprecated option`.

Both are checked because squash merge uses the PR title while rebase merge keeps
the individual commit messages. The `Conventional Commits` CI check enforces this
on pull requests to `main`; a maintainer makes it mandatory via branch
protection (or ruleset) on `main`. Release and version-bump semantics are owned by
[ADR-021](docs/adr/021-unified-release-train.md).
