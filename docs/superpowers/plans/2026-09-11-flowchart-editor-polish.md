# Flowchart Editor Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the flowchart editor progressively disclose its properties panel, use scroll-to-pan and Ctrl-scroll-to-zoom, fill its available layout, and provide ProcessOn-like edge editing feedback without changing the persisted diagram schema.

**Architecture:** Keep AntV X6 as the graph engine. `FlowchartWorkspace` owns panel visibility and layout; `FlowchartCanvas` owns wheel/context-menu gestures and X6 edge tooling; `FlowchartProperties` exposes explicit close/focus affordances. The existing adapter continues to serialize node positions and edge vertices.

**Tech Stack:** Vue 3 Composition API, TypeScript, AntV X6 3.1.8, Lucide icons, Node test runner, Vite/vue-tsc.

**Spec:** `docs/superpowers/specs/2026-09-11-flowchart-editor-polish-design.md`

## Global Constraints

- Do not change the backend API or `FlowchartDiagram` schema.
- Keep `lucide-vue-next` for icons; no emoji or new dependency.
- Preserve keyboard shortcuts, read-only/mobile behavior, theme variables, export, undo/redo, and viewport persistence.
- Component-specific styles stay in scoped component styles.
- Run `npm test`, `npm run build`, and `npm run test:ui:build` before completion.

---

### Task 1: Add pure interaction contract tests

**Files:**
- Modify: `first-project-web/src/utils/flowchartEditor.ts` (add small exported pure helpers only if needed)
- Test: `first-project-web/tests/flowchartEditor.test.ts`

**Interfaces:**
- Produces stable, DOM-free decisions for wheel gesture classification and property-panel opening triggers, so UI behavior can be regression-tested without mocking X6.

- [x] **Step 1: Write failing tests** for `classifyCanvasWheel({ ctrlKey, metaKey, deltaX, deltaY })` and `shouldOpenProperties('single'|'double'|'context')`, asserting ordinary wheel pans, Ctrl/Meta wheel zooms, and only double/context triggers auto-open.
- [x] **Step 2: Run** `npm test -- --test-name-pattern="canvas wheel|properties trigger"` and confirm the new tests fail because the helpers do not exist.
- [x] **Step 3: Implement** the minimal typed helpers in `flowchartEditor.ts` with no DOM/X6 dependency.
- [x] **Step 4: Re-run** the focused tests and then the existing `flowchartEditor` tests; all must pass.

### Task 2: Implement property-panel progressive disclosure

**Files:**
- Modify: `first-project-web/src/components/flowcharts/FlowchartWorkspace.vue`
- Modify: `first-project-web/src/components/flowcharts/FlowchartCanvas.vue`
- Modify: `first-project-web/src/components/flowcharts/FlowchartProperties.vue`

**Interfaces:**
- `FlowchartCanvas` emits `edit-label` for double/context edit and `toggle-properties` for the toolbar.
- `FlowchartProperties` emits a new `close` event and keeps the existing `change`, `action`, and `select` events.

- [x] **Step 1: Add the failing UI-contract assertions** to the existing source-level/component fixture checks (or a focused DOM test if available): default `showProperties` is false, context-menu and double-click call the same open/focus path, and the properties header exposes a close button.
- [x] **Step 2: Run the focused check** and confirm it fails against the current always-open panel/no context-menu handler.
- [x] **Step 3: Change Workspace state** to default closed, add `propertiesPinned`/open-close helpers, open and focus after edit events with `nextTick`, auto-collapse after blank selection unless explicitly pinned, and wrap the panel in a transition without retaining width when closed.
- [x] **Step 4: Change Canvas events** to handle `cell:contextmenu` and `blank:contextmenu`, select the cell, prevent the native menu, and emit the existing edit event; keep the toolbar toggle accessible.
- [x] **Step 5: Add a close button** and `close` emit in `FlowchartProperties`, with `aria-label`, focus styling, and no changes to field update semantics.
- [x] **Step 6: Run typecheck/build** and focused UI checks; fix event typing or mount-order issues before proceeding.

### Task 3: Correct wheel gestures and viewport behavior

**Files:**
- Modify: `first-project-web/src/components/flowcharts/FlowchartCanvas.vue`
- Test: `first-project-web/tests/flowchartEditor.test.ts` (extend pure helper coverage if required)

**Interfaces:**
- X6 mousewheel config receives `modifiers: ['ctrl']` plus `zoomAtMousePosition: true`.
- A canvas-local non-passive wheel handler translates the graph for ordinary wheel deltas and is removed on unmount.

- [x] **Step 1: Add/extend failing helper tests** for Ctrl and Meta classification and delta normalization (including `deltaMode` line/page values).
- [x] **Step 2: Run the focused tests** and verify the expected failures.
- [x] **Step 3: Configure X6** with Ctrl-only zoom bounds, and add a host wheel listener that pans only when no Ctrl/Meta modifier is present; leave palette/property scroll untouched.
- [x] **Step 4: Update the hint and ARIA/title copy** to say “滚轮平移 · Ctrl + 滚轮缩放”, and ensure panning does not interfere with space/manual pan mode.
- [x] **Step 5: Verify** focused tests, typecheck, and a local browser smoke check of zoom percentage and translation persistence.

### Task 4: Refine full-height layout and edge editing visuals

**Files:**
- Modify: `first-project-web/src/components/flowcharts/FlowchartWorkspace.vue`
- Modify: `first-project-web/src/components/flowcharts/FlowchartCanvas.vue`
- Modify: `first-project-web/src/components/flowcharts/FlowchartProperties.vue`

**Interfaces:**
- Persisted node/edge data remains unchanged; only X6 runtime router/connector/tool styling changes.

- [x] **Step 1: Update layout styles** to use `height:100%`, `flex:1`, and `min-height:0` through the writing-mode parent; remove the fixed 560px floor while retaining a safe mobile minimum.
- [x] **Step 2: Update X6 edge runtime** to use rounded orthogonal connectors, stable Manhattan padding/step, explicit vertex/segment tool options, and stronger magnet/edge hover states.
- [x] **Step 3: Replace black-block tool styling** with compact white/blue handles, larger invisible hit areas where possible, and reduced-motion-safe transitions.
- [x] **Step 4: Update responsive styles** for 1100px and 767px breakpoints so the palette and optional panel do not cause horizontal overflow.
- [x] **Step 5: Run focused tests and inspect the generated diagram fixture** to ensure vertices still serialize and validate.

### Task 5: Full verification and handoff

**Files:**
- No new production files; update docs only if verification notes are needed.

- [x] **Step 1: Run** `npm test` from `first-project-web`; record the complete pass count.
- [x] **Step 2: Run** `npm run build`; confirm vue-tsc and Vite exit 0.
- [x] **Step 3: Run** `npm run test:ui:build`; confirm the production UI fixture compiles.
- [x] **Step 4: Launch the isolated UI fixture** and check desktop plus 390px: default hidden properties, double/right-click reveal, ordinary/Ctrl wheel, panel close expansion, and edge vertex/segment drag.
- [x] **Step 5: Review `git diff`** for scope, preserve unrelated worktree changes, and report exact verification evidence.
