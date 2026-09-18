/**
 * Title-only lint configuration: the Conventional Commits workflow checks the
 * pull-request title through stdin, and commitlint's default ignore wildcards
 * ("Merge ...", "Revert ...", ...) also filter stdin reads. Git-generated
 * merge/revert lines are valid commit-message forms that must be skipped in
 * commit-range reads, but a PR *title* adopting those forms must fail the
 * grammar. This config therefore keeps every grammar rule from the shared
 * configuration while disabling the default ignores for the title read.
 */
import baseConfig from './commitlint.config.mjs';

export default {
  ...baseConfig,
  defaultIgnores: false,
};
