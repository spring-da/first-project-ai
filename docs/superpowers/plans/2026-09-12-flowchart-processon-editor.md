# ProcessOn 风格流程图交互重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace jumpy X6 edge-segment editing with a stable ProcessOn-like editor and tighten the surrounding flowchart UI.

**Architecture:** Keep X6 for graph rendering, persistence, selection, connection and history. Add a pure geometry/state utility for orthogonal edge editing and a canvas-local overlay that performs preview edits, committing one normalized vertices update on release. Workspace and properties styles are refined without changing the public diagram schema.

**Tech Stack:** Vue 3 Composition API, TypeScript, AntV X6 3.1.8, lucide-vue-next, Node test runner, Vite.

**Spec:** `docs/superpowers/specs/2026-09-12-flowchart-processon-editor-design.md`

## Global Constraints

- Do not change the backend API or `FlowchartDiagram` schema.
- Keep lucide-vue-next and existing theme variables; add no dependencies.
- Preserve save/autosave, undo/redo, export, history, read-only/mobile behavior and viewport persistence.
- Production code must be preceded by a failing test; run the relevant focused test before each implementation step.
- Keep all document/graph listeners cleaned up on unmount and on cancelled drags.

---

### Task 1: Pure orthogonal edge geometry contract

**Files:**
- Create: `first-project-web/src/utils/flowchartEdgeEditor.ts`
- Modify: `first-project-web/tests/flowchartEditor.test.ts`

**Interfaces:**
- `type EdgePoint = { x: number; y: number }`
- `type OrthogonalSegment = { index: number; start: EdgePoint; end: EdgePoint; axis: 'x' | 'y'; midpoint: EdgePoint; length: number }`
- `normalizeOrthogonalPoints(points, minGap?): EdgePoint[]`
- `describeOrthogonalSegments(points, minLength?): OrthogonalSegment[]`
- `moveOrthogonalSegment(points, segmentIndex, delta, options?): EdgePoint[]`
- `insertOrthogonalVertex(points, segmentIndex, point): EdgePoint[]`
- `removeOrthogonalVertex(points, vertexIndex, minGap?): EdgePoint[]`
- `createEdgeDragSnapshot(vertices, router): { vertices; router }` and `restoreEdgeDragSnapshot(snapshot)`

- [ ] Write failing tests for normalization, axis-locked movement with adjacent vertices, clamping/minimum gap, insertion/removal and snapshot cloning.
- [ ] Run `npm test -- --test-name-pattern="orthogonal edge|edge drag"` and confirm the new tests fail for missing exports.
- [ ] Implement the smallest pure functions; never read DOM/X6 in this module.
- [ ] Re-run focused tests and the whole `flowchartEditor` test file.

### Task 2: Canvas edge editor overlay

**Files:**
- Modify: `first-project-web/src/components/flowcharts/FlowchartCanvas.vue`
- Modify: `first-project-web/src/utils/flowchartAdapter.ts` only if a runtime router helper is needed (no schema changes)

**Interfaces:**
- Canvas-local state exposes only visual/editor state; parent continues to receive `change` with a normal `FlowchartDiagram`.
- Overlay pointer handlers call the Task 1 geometry functions and use `edge.setVertices` inside one history batch.

- [ ] Add a failing source/DOM contract test proving native `segments` tools are not installed and the custom editor layer has accessible handles.
- [ ] Remove `edgeToolOptions` native segment/vertex installation for editable edges.
- [ ] Add an overlay that derives route points from `EdgeView`, renders bend/segment handles only for the selected edge, and updates on scale/translate/resize/cell changes.
- [ ] Implement click-to-insert, double-click-to-remove, axis-locked drag preview, pointer capture, Esc cancel and one-shot commit; suppress intermediate `change` emissions while previewing.
- [ ] Keep endpoint magnet reconnection and label editing intact; hide the overlay during export and read-only mode.
- [ ] Add keyboard nudge and status hint for the selected edge; clean every listener on unmount.
- [ ] Run focused unit tests, typecheck and the existing UI build.

### Task 3: UI hierarchy and layout polish

**Files:**
- Modify: `first-project-web/src/components/flowcharts/FlowchartWorkspace.vue`
- Modify: `first-project-web/src/components/flowcharts/FlowchartCanvas.vue`
- Modify: `first-project-web/src/components/flowcharts/FlowchartProperties.vue`

- [ ] Add failing source checks for compact toolbar grouping, edge-editor state classes and no fixed canvas whitespace floor.
- [ ] Tighten palette width/padding and make it collapsible on medium widths; let the canvas center content with a bounded safe padding.
- [ ] Restyle toolbar/context bar, node selection, ports, edge hover/selection and custom handles with reduced-motion-safe transitions and focus rings.
- [ ] Keep properties progressive disclosure, close affordance, mobile minimum height and dark theme variables.
- [ ] Update hint/copy for click-to-insert, double-click-to-remove, Esc cancel and Ctrl-scroll zoom.
- [ ] Run typecheck and source/UI contract tests.

### Task 4: Browser regression and verification

**Files:**
- Modify: `first-project-web/tests/ui/flowchart-fixture.test.ts` or `tests/ui/flowchart-fixture.ts` only if deterministic fixture coverage is needed.

- [ ] Extend isolated browser checks for edge selection, segment drag preview, insertion/removal, Esc cancel, persistence after reload, zoom/pan and 390px layout.
- [ ] Run `npm test`, `npm run build`, and `npm run test:ui:build` from `first-project-web`.
- [ ] Inspect the generated SVG/PNG export and confirm editor handles/preview overlays are absent.
- [ ] Review `git diff` and leave unrelated user changes untouched.
