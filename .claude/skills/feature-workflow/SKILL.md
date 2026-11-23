---
name: feature-workflow
description: End-to-end execution workflow for implementing features, environment setup, or code review fixes in Orbita API. Use whenever starting a new task, feature slice, or applying review feedback.
---

# Feature Workflow Skill

Follow `AGENTS.md` (Sections 2 and 9) for non-negotiable constraints, dependency approval, no-invention rules, and test integrity.

This workflow governs the **Coding + Testing** session. Code review and git commits happen only after all tests pass and the user approves the handoff.

## Phase 1: Context & Contract Check

Before proposing any code:
1. Read `AGENTS.md`, `docs/data-model.md`, and `docs/endpoints.md` (when relevant to the task).
2. Inspect existing files in the target module to match current conventions.
3. If any requirement, contract detail, or edge case is missing or ambiguous, stop and list the exact questions before proceeding.

## Phase 2: Implementation Plan (Approval Gate)

Do **not** edit any files yet. Present a short, concrete plan containing:
- **Scope:** Exactly what this slice will implement (and what stays untouched).
- **Files:** List of files to create or modify.
- **Dependencies:** Any new `pom.xml` dependency needed, with name, version, and reason (if none, state explicitly).
- **Test plan:** Unit and integration test scenarios to cover (success, validation, domain errors, security/ownership).
- **Open questions:** Any decision needed from the user.

**Hard gate:** Stop and wait for explicit user approval of the plan (and any dependencies) before writing code.

## Phase 3: Implementation & Verification Loop

Once the plan is approved:
1. Apply the relevant technical skills for the task (`spring-modular-mvc`, `firebase-firestore`, `spring-data-redis`).
2. Implement strictly the approved scope — no unrequested refactors or extras.
3. Write the planned unit, integration, and module verification tests following `automated-testing`.
4. Run `./mvnw verify` and inspect the real output.
5. If any test or build step fails:
   - Diagnose and fix the actual root cause in production or test code.
   - Re-run `./mvnw verify` until the entire suite passes.
   - Never weaken assertions, skip tests, hardcode test inputs, or fake behavior to force a green build. If blocked by a real issue you cannot resolve, stop and report it.

## Phase 4: Handoff to Code Review

Do not report the task as complete until `./mvnw verify` passes. When ready, provide a concise handoff summary:
- Files created or modified.
- Exact `./mvnw verify` result (stating honestly if anything could not be run).
- Any out-of-scope observations worth addressing later (left untouched).

Stop here so the user can run **Code Review** in a clean session. Do **not** run `git commit` unless explicitly asked.

## Phase 5: Code Review Fix Loop

When the user returns with feedback from Code Review:
1. Apply only the requested fixes. If a review suggestion conflicts with `AGENTS.md` or alters architecture/data models, confirm with the user first.
2. Add or update tests if the fix changes behavior or closes a gap.
3. Re-run `./mvnw verify` and confirm everything passes before handing back for re-review.
