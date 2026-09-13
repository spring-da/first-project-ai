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
