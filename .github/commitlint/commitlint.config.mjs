/**
 * Conventional Commits grammar used by the Conventional Commits CI guardrail.
 *
 * Extends @commitlint/config-conventional and keeps its remaining defaults
 * (type/subject/scope casing, subject-full-stop), with these policy overrides:
 *
 * - type-enum: the project allowlist (see CONTRIBUTING.md).
 * - header/body/footer length limits are disabled; the grammar does not
 *   enforce message lengths.
 * - A custom rule enforces a well-formed breaking-change marker on commit
 *   messages: any line whose token is a spelling/case variant of
 *   BREAKING CHANGE / BREAKING-CHANGE must be exactly
 *   `BREAKING CHANGE: <non-empty explanation>` or
 *   `BREAKING-CHANGE: <non-empty explanation>` (exact-case token, colon,
 *   non-empty explanation; the explanation may continue on following lines).
 */
export default {
  extends: ['@commitlint/config-conventional'],
  plugins: [
    {
      rules: {
        'breaking-change-format': ({ raw }) => {
          const lines = String(raw).split(/\r?\n/);
          for (const [index, line] of lines.entries()) {
            const token = line.replace(/^[ \t]+/, '');
            if (!/^breaking[\s_-]+change/i.test(token)) {
              continue;
            }
            const matches = token.match(/^BREAKING[ -]CHANGE:(.*)$/);
            let explained =
              matches !== null && /[^ \t]/.test(matches[1] ?? '');
            if (!explained) {
              // The explanation may be continued on the following footer
              // lines; a blank line ends the footer paragraph.
              for (let j = index + 1; j < lines.length; j++) {
                if (lines[j].trim() === '') {
                  break;
                }
                if (/[^ \t]/.test(lines[j])) {
                  explained = true;
                  break;
                }
              }
            }
            if (!explained) {
              return [
                false,
                `breaking change marker must be "BREAKING CHANGE: <explanation>" or "BREAKING-CHANGE: <explanation>" with a non-empty explanation, found: ${JSON.stringify(token)}`,
              ];
            }
          }
          return [true, ''];
        },
      },
    },
  ],
  rules: {
    'type-enum': [
      2,
      'always',
      [
        'feat',
        'fix',
        'perf',
        'refactor',
        'docs',
        'test',
        'build',
        'ci',
        'chore',
        'revert',
      ],
    ],
    'breaking-change-format': [2, 'always'],
    'header-max-length': [0],
    'body-max-line-length': [0],
    'footer-max-line-length': [0],
  },
};
