# Task 1 report: pure workbench preferences

Implemented immutable flowchart workbench preference helpers.

## Changes

- Added `FlowchartPanel` and `FlowchartWorkbenchPreferences` types.
- Added default state creation and panel-specific immutable toggling.
- Added recent-shape insertion with deduplication, prepending, and configurable cap (default five).
- Added supported-shape filtering for recent kinds, including de-duplication and preservation of order.
- Added tolerant persisted-preference parsing for JSON strings and plain values; malformed input falls back to defaults and valid fields are normalized.
- Added `isFlowchartShapeKind` to the existing flowchart editor utility for shared runtime validation.
- Added focused state tests covering all helper behaviors.

## Verification

- Focused preference/shape tests pass.
- Full `npm test` passes: 172 tests, 172 passed.
- `npm run build` passes (`vue-tsc -b` and Vite production build).
- `git diff --check` reports no whitespace errors.

## Review fix: RED/GREEN evidence

The RED phase was reproduced after temporarily removing only the new state module (the committed implementation was restored immediately afterward). The focused command was:

```text
node --experimental-strip-types --test tests/flowchartEditorState.test.ts
```

Exact relevant failure output:

```text
node:internal/modules/esm/resolve:271
    throw new ERR_MODULE_NOT_FOUND(
          ^

Error [ERR_MODULE_NOT_FOUND]: Cannot find module 'E:\WorkProject\codex\first-project-ai\.worktrees\flowchart-workbench-rebuild\first-project-web\src\utils\flowchartEditorState.ts' imported from E:\WorkProject\codex\first-project-ai\.worktrees\flowchart-workbench-rebuild\first-project-web\tests\flowchartEditorState.test.ts
✖ tests\flowchartEditorState.test.ts (69.6052ms)
ℹ tests 1
ℹ suites 0
ℹ pass 0
ℹ fail 1
```

This failed because the test imported the not-yet-created production module, demonstrating the intended missing-module RED state. After restoring the implementation exactly, the GREEN focused run reported:

```text
✔ workbench preferences toggle only the requested panel (0.7921ms)
✔ recent shapes deduplicate, prepend and cap at five entries (0.123ms)
✔ visible recent shapes only includes supported kinds (0.0793ms)
✔ malformed persisted preferences fall back to defaults (0.1001ms)
✔ persisted preferences are normalized while retaining valid values (0.0821ms)
ℹ tests 5
ℹ pass 5
ℹ fail 0
```

The subsequent full run remained green: `npm test` reported 172 tests, 172 passed, 0 failed.
