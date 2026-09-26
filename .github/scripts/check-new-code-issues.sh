#!/usr/bin/env bash
# Check SonarCloud for unresolved issues in the new-code period.
#
# Required env:
#   SONAR_TOKEN — SonarCloud token (never passed on the argv)
# Optional env:
#   PULL_REQUEST — PR number (CI PR analysis)
#   BRANCH — branch name (non-default branch analysis)
#            Omit both for default-branch (main) analysis.
#
# Usage:
#   ./check-new-code-issues.sh                 # fail closed: exit 1 when total != 0 (lists on failure)
#   ./check-new-code-issues.sh --list          # always list issue key + message lines
#   ./check-new-code-issues.sh --require-zero  # accepted alias; zero is already the default
#   ./check-new-code-issues.sh --allow-nonzero # print total only; exit 0 if total is a valid number
#
# Exit codes:
#   0 — HTTP/JSON ok and unresolved new-code total is 0 (or any valid total with --allow-nonzero)
#   non-zero — missing deps/token, HTTP error, malformed response, or unresolved new-code issues
set -euo pipefail

PAGE_SIZE=100
LIST=0
ALLOW_NONZERO=0
REQUIRE_ZERO_FLAG=0
for arg in "$@"; do
  case "$arg" in
    --list) LIST=1 ;;
    --require-zero) REQUIRE_ZERO_FLAG=1 ;; # default behavior; kept for callers that already pass it
    --allow-nonzero) ALLOW_NONZERO=1 ;;
    *)
      echo "usage: $0 [--list] [--require-zero] [--allow-nonzero]" >&2
      exit 2
      ;;
  esac
done

if [[ "$REQUIRE_ZERO_FLAG" -eq 1 && "$ALLOW_NONZERO" -eq 1 ]]; then
  echo "conflicting flags: --require-zero and --allow-nonzero" >&2
  exit 2
fi

if [[ -z "${SONAR_TOKEN:-}" ]]; then
  echo "SONAR_TOKEN is required" >&2
  exit 1
fi

for cmd in curl jq; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "required command not found: $cmd" >&2
    exit 1
  fi
done

urlencode() {
  local value="$1"
  jq -nr --arg v "$value" '$v|@uri'
}

COMPONENT_KEY="kazemek_jsonapi-java"
BASE_URL="https://sonarcloud.io/api/issues/search?componentKeys=${COMPONENT_KEY}&resolved=false&inNewCodePeriod=true&ps=${PAGE_SIZE}"
if [[ -n "${PULL_REQUEST:-}" ]]; then
  BASE_URL="${BASE_URL}&pullRequest=$(urlencode "${PULL_REQUEST}")"
elif [[ -n "${BRANCH:-}" ]]; then
  BASE_URL="${BASE_URL}&branch=$(urlencode "${BRANCH}")"
fi

fetch_page() {
  local page="$1"
  curl -sS --fail-with-body -q --config - \
    "${BASE_URL}&p=${page}" \
    <<EOF
user = "${SONAR_TOKEN}:"
EOF
}

list_issues() {
  local issues_json='[]'
  local page=1
  local collected=0
  local response="$RESPONSE"
  local page_count

  while true; do
    if [[ "$page" -gt 1 ]]; then
      response="$(fetch_page "$page")"
    fi

    if ! printf '%s' "$response" | jq -e '.issues | type == "array"' >/dev/null; then
      echo ".issues must be a JSON array" >&2
      exit 1
    fi

    page_count="$(printf '%s' "$response" | jq '.issues | length')"
    issues_json="$(
      jq -n --argjson acc "$issues_json" --argjson page "$(printf '%s' "$response" | jq '.issues')" \
        '$acc + $page'
    )"
    collected=$((collected + page_count))

    if [[ "$collected" -ge "$TOTAL" || "$page_count" -lt "$PAGE_SIZE" ]]; then
      break
    fi
    page=$((page + 1))
  done

  printf '%s' "$issues_json" | jq -r '.[] | "\(.key) \(.message)"'
}

RESPONSE="$(fetch_page 1)"
TOTAL="$(printf '%s' "$RESPONSE" | jq -e '.total | select(type == "number" and . >= 0 and . == floor)')"
printf '%s\n' "$TOTAL"

# List whenever asked, and always on failure so agents/CI see what to fix without a second flag.
if [[ "$LIST" -eq 1 || ("$ALLOW_NONZERO" -eq 0 && "$TOTAL" -ne 0) ]]; then
  list_issues
fi

if [[ "$ALLOW_NONZERO" -eq 0 && "$TOTAL" -ne 0 ]]; then
  echo "expected 0 unresolved new-code issues, found ${TOTAL}" >&2
  exit 1
fi
