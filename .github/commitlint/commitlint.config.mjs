// Shared commitlint configuration for the Conventional Commits CI guardrail.
// Standard @commitlint/config-conventional behavior with no project-specific
// rule overrides; breaking markers (`!`, `BREAKING CHANGE:`) are accepted as
// the underlying tooling defines them.
export default {
  extends: ['@commitlint/config-conventional'],
};
