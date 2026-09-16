---
name: implementation-review
description: Produces an evidence-based code review against a requested work outcome and writes the result to `.agentWork/.session/`. Use when the user explicitly requests an implementation review, or when implement-work delegates review to a fresh-context subagent.
disable-model-invocation: true
---

# Implementation Review

Ask: **Does this change correctly satisfy the requested work and repository rules?**

Evaluate:

```text
requested outcome / acceptance intent
+ actual diff
+ repository contracts/docs/tests
+ applicable engineering gates
```

Do not fix findings or modify sources unless the user separately asks. Do not judge primarily on
whether the change matches a local working plan.

## Resolve inputs

1. Take **requested outcome** and **acceptance intent** from the task prompt (preferred) or from
   fallback `.agentWork/.session/work-context.md`. If both are missing, verdict is **Blocked**—do
   not reconstruct work from a local plan, branch names, trackers, Git history, or inferred diffs.
2. Optionally read a local plan as orientation only. Harmless plan divergence is not a defect.
3. Read `AGENTS.md`, affected module READMEs/package docs, linked ADRs/conformance, and Vision when
   product boundaries are implicated.
4. Determine the change set from supplied diff/PR/range, else branch + uncommitted Git metadata.
5. State the reviewed change-set boundary. Never imply unexamined code was reviewed.

## Review

Map the requested outcome and acceptance intent to production, test, configuration, and
documentation evidence. Check:

1. Correctness and scope vs the requested outcome; regressions; missing validation/tests;
   Vision/ADR conflicts; accidental out-of-scope work.
2. Nullness (ADR-008) when implicated.
3. `module-docs` when public module surface changed.
4. Knowledge sync: Snapshot surfaces updated or justified unchanged; no durable fact only in a
   disposable local plan; one canonical owner per new durable fact.
5. Run or verify applicable gates from `AGENTS.md`; record commands and outcomes.

Severity: **Critical** / **High** / **Medium** / **Low**.

Do not reopen architecture as personal taste. Material divergence from accepted ADRs/Vision/public
contracts remains a finding.

## Verdict

- **Pass:** no actionable findings; acceptance intent has sufficient evidence.
- **Changes required:** actionable findings remain.
- **Blocked:** cannot conclude because required outcome/intent, implementation, or evidence is
  unavailable.

## Artifact

Write `.agentWork/.session/implementation-review-<basename>.md` using [reference.md](reference.md).
Replace on every re-review. Report path and verdict.
