---
name: module-docs
description: Assesses and updates affected module documentation proportionally (README, package-info, public entry-point Javadoc, root registration). Use when adding a submodule, changing public packages or entry points, changing validate/read flows or non-goals, or refreshing module docs.
disable-model-invocation: true
---

# Module Docs

Update only the canonical documentation owners affected by a module change. A complete result is
usable and navigable, not mechanically uniform with other modules.

## When to run

Use this skill when:

- adding a submodule under `settings.gradle.kts`;
- changing public packages, entry points, validate/read flows, non-goals, or module-local invariants;
- the user asks to create or refresh module documentation.

Skip it when internal code or tests change without affecting any documented contract.

## Ownership rules

- A module README owns current module purpose, capability, consumption entry points, non-goals, and
  genuinely module-local maintenance constraints.
- `package-info.java` owns package responsibility, public/internal boundaries, and package-local
  invariants.
- Focused public Javadoc owns API semantics. Do not copy method contracts or overload inventories
  into the README.
- The root README owns human-readable module registration; `settings.gradle.kts` owns actual build
  membership.
- Architecture, conformance, ADRs, build policy, and workflow stay with their existing canonical
  owners. Link to them instead of restating them.

Documentation additions must fill a missing contract or replace duplication. Uniform section
completion is not a goal.

## Workflow

1. Resolve the target module against `settings.gradle.kts`. Planned or nonexistent modules have no
   usable entry points; never invent current capability for them.
2. Read the module README, every affected production `package-info.java`, and the focused Javadoc on
   changed public entry points. Read only the directly relevant architecture, conformance, or ADR
   sections.
3. Identify each changed durable fact and its canonical owner before editing. Update only those
   owners and navigation needed to reach them.
4. Keep the README proportional to the module. Include, when useful:
   - a concise purpose and current capability;
   - a package or public-entry-point map when it materially improves navigation;
   - enough usage to make the primary consumption path understandable;
   - module-specific non-goals or differences;
   - genuinely local maintainer invariants;
   - a small set of relevant canonical links.

   Equivalent headings are acceptable. Omit empty or redundant sections. Never duplicate one
   inventory as a table, diagram, and prose list.
5. Include an example only when prose plus a focused entry-point link would be materially less clear.
   Do not require examples per module or capability, and do not document overload matrices.
6. Ensure every production package has `package-info.java`. Production package docs are
   `@NullMarked` with JSpecify, remain role-focused, and state absence versus explicit wire null when
   a document-model package needs that distinction.
7. Update focused Javadoc when the public entry-point contract changed. Do not turn an unrelated
   module-doc task into cleanup of existing Javadocs or documentation of every type.
8. Register and link a newly built module in the root README. Keep `AGENTS.md` generic; do not add
   one route or link per module.
9. Apply the deletion test: if a README section has no unique module contract after canonical links
   are considered, remove it.
10. Verify package and entry-point maps against source, check changed links, and report every path
    created or updated.

## Guardrails

- Keep only module-local contributor constraints. Repository build, nullness, testing, coverage,
  workflow, and architecture policy remain in their canonical owners unless the module has a real
  local specialization.
- Root registration does not require every module README to adopt the same headings or density.
- Package documentation and Javadoc are mandatory where their contracts belong; README coverage is
  conditional on information value.
