// Title-only configuration: the workflow lints the pull-request title via
// stdin, and commitlint's default ignores would also skip stdin reads (e.g. a
// title starting with "Merge ..."). The commit range keeps the shared
// configuration with its default ignores.
import baseConfig from './commitlint.config.mjs';

export default {
  ...baseConfig,
  defaultIgnores: false,
};
