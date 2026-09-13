# Desktop Flowchart Workbench Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the PC flowchart experience around a guided creation panel and a collapsible three-column professional workbench while preserving existing flowchart data and server behavior.

**Architecture:** `FlowchartWorkspace` stays route-facing and owns loading, persistence, history, sharing and leave protection. It shows `FlowchartStarter` for new documents, then composes a new `FlowchartEditorShell` from independent node-library, canvas and inspector components. Canvas operations emit typed edits into the existing `FlowchartSession`; the X6 adapter continues to serialize the unchanged `FlowchartDiagram` format.

**Tech Stack:** Vue 3.5 Composition API with TypeScript, AntV X6 3.1.8, Pinia, Vue Router 4, lucide-vue-next, Node test runner, Vite.

**Spec:** `docs/superpowers/specs/2026-09-13-desktop-flowchart-rebuild-design.md`

## Global Constraints

- Target desktop widths of 1280px and above; do not introduce a mobile editing layout.
- Do not change the backend API, database schema, `FlowchartDiagram` schema version, history format or import/export source format.
- Keep automatic cloud saving, IndexedDB local drafts, conflict recovery, route-leave protection, history, sharing, import and PNG/SVG/JSON export.
- Continue using Composition API, typed emits, lucide-vue-next and current CSS variables; add no dependency.
- Every interactive control stays keyboard reachable with visible focus and a button/keyboard alternative to drag-only work.
- Respect `prefers-reduced-motion`; use only transform/opacity for nonessential transitions.
- Before every production edit, write a focused failing test; finish every task with focused tests and a commit.

---

## Planned File Structure

| Path | Responsibility |
| --- | --- |
| `src/utils/flowchartEditorState.ts` | Pure three-column preferences and recent-shape logic. |
| `src/components/flowcharts/FlowchartStarter.vue` | Blank/template/import entry UI. |
| `src/components/flowcharts/NodeLibraryPanel.vue` | Search, categories, recent nodes, click and drag creation. |
| `src/components/flowcharts/InspectorPanel.vue` | Canvas/node/edge/help inspector states. |
| `src/components/flowcharts/FlowchartEditorShell.vue` | Three-column grid, top bar and panel composition. |
| `src/components/flowcharts/FlowchartCanvas.vue` | X6 graph, viewport, selected-object commands and edge overlay. |
| `src/components/flowcharts/FlowchartWorkspace.vue` | Existing session/persistence plus starter and shell composition. |
| `src/views/KnowledgeView.vue` | Opens the starter; removes the old new-flowchart modal. |
| `tests/flowchartEditorState.test.ts` | Pure workbench state tests. |
| `tests/flowchartStarterContract.test.ts` | Starter path and accessibility source contracts. |
| `tests/flowchartWorkbenchContract.test.ts` | Three-column shell and panel source contracts. |

## Task 1: Establish pure workbench preferences

**Files:**
- Create: `first-project-web/src/utils/flowchartEditorState.ts`
- Create: `first-project-web/tests/flowchartEditorState.test.ts`
- Modify: `first-project-web/src/utils/flowchartEditor.ts`

**Interfaces:**
- Produces `type FlowchartPanel = 'library' | 'inspector'`.
- Produces `interface FlowchartWorkbenchPreferences { libraryCollapsed: boolean; inspectorCollapsed: boolean; recentKinds: FlowNodeKind[] }`.
- Produces `defaultWorkbenchPreferences()`, `toggleWorkbenchPanel(state, panel)`, `rememberRecentShape(state, kind, limit = 5)`, `visibleRecentKinds(kinds)`, and `parseWorkbenchPreferences(value)`.

- [ ] **Step 1: Write the failing state tests**

```ts
import assert from 'node:assert/strict'
import test from 'node:test'
import { defaultWorkbenchPreferences, rememberRecentShape, toggleWorkbenchPanel } from '../src/utils/flowchartEditorState.ts'

test('workbench preferences toggle only the requested panel', () => {
  const state = defaultWorkbenchPreferences()
  assert.deepEqual(toggleWorkbenchPanel(state, 'library'), { ...state, libraryCollapsed: true })
})

test('recent shapes deduplicate, prepend and cap at five entries', () => {
  let state = defaultWorkbenchPreferences()
  for (const kind of ['rectangle', 'diamond', 'rectangle', 'database', 'io', 'text'] as const) state = rememberRecentShape(state, kind)
  assert.deepEqual(state.recentKinds, ['text', 'io', 'database', 'rectangle', 'diamond'])
})
```

- [ ] **Step 2: Run the focused tests**

Run: `npm test -- --test-name-pattern="workbench preferences|recent shapes"`

Expected: FAIL because the module is missing.

- [ ] **Step 3: Implement immutable preference helpers**

```ts
export function defaultWorkbenchPreferences(): FlowchartWorkbenchPreferences {
  return { libraryCollapsed: false, inspectorCollapsed: false, recentKinds: [] }
}
export function rememberRecentShape(state: FlowchartWorkbenchPreferences, kind: FlowNodeKind, limit = 5): FlowchartWorkbenchPreferences {
  return { ...state, recentKinds: [kind, ...state.recentKinds.filter(item => item !== kind)].slice(0, limit) }
}
```

Make `parseWorkbenchPreferences` return defaults for malformed values; filter `visibleRecentKinds` against `FLOWCHART_SHAPES`.

- [ ] **Step 4: Run focused tests**

Run: `npm test -- --test-name-pattern="workbench preferences|recent shapes|shape palette"`

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add first-project-web/src/utils/flowchartEditorState.ts first-project-web/src/utils/flowchartEditor.ts first-project-web/tests/flowchartEditorState.test.ts
git commit -m "feat: add flowchart workbench state helpers"
```

## Task 2: Build the guided new-flowchart starter

**Files:**
- Create: `first-project-web/src/components/flowcharts/FlowchartStarter.vue`
- Create: `first-project-web/tests/flowchartStarterContract.test.ts`
- Modify: `first-project-web/src/components/flowcharts/FlowchartWorkspace.vue`

**Interfaces:**
- `FlowchartStarter` receives template descriptors with `kind: 'sequence' | 'decision' | 'architecture'`.
- Emits `start-blank`, `start-template(kind)`, `import`, and `close`.
- A new workspace creates a session only after a start event; an existing `documentId` opens directly.

- [ ] **Step 1: Write the failing starter contracts**

```ts
test('starter exposes blank, template and import paths with typed actions', () => {
  assert.match(source, /emit\\('start-blank'\\)/)
  assert.match(source, /emit\\('start-template',\\s*template\\.kind\\)/)
  assert.match(source, /emit\\('import'\\)/)
  assert.match(source, /aria-label="新建流程图起始面板"/)
})

test('workspace shows starter before creating a new session', () => {
  assert.match(workspaceSource, /v-if="showStarter"/)
  assert.match(workspaceSource, /@start-blank="startBlank"/)
})
```

- [ ] **Step 2: Run the starter contracts**

Run: `npm test -- --test-name-pattern="starter exposes|shows starter"`

Expected: FAIL because the component and state do not exist.

- [ ] **Step 3: Implement starter and workspace entry**

Use `LayoutTemplate`, `FilePlus2`, `Upload` and `ArrowRight`. Render semantic buttons for blank and import plus preview cards for all templates. Keep the real file input in the workspace; starter only emits `import`.

```vue
<script setup lang="ts">
const emit = defineEmits<{ 'start-blank': []; 'start-template': [kind: TemplateKind]; import: []; close: [] }>()
</script>
<template>
  <section class="flowchart-starter" aria-label="新建流程图起始面板">
    <button type="button" @click="emit('start-blank')">从空白画布开始</button>
    <button type="button" @click="emit('import')">导入流程图</button>
    <button v-for="template in templates" :key="template.kind" type="button" @click="emit('start-template', template.kind)">{{ template.title }}</button>
  </section>
</template>
```

Set `showStarter = ref(!props.documentId)`. Route `startBlank`, `startTemplate`, and successful import through one `startNewDraft(draft)` function that installs a session, closes starter and focuses the title after mount.

- [ ] **Step 4: Run focused tests**

Run: `npm test -- --test-name-pattern="starter|template|import flowchart"`

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add first-project-web/src/components/flowcharts/FlowchartStarter.vue first-project-web/src/components/flowcharts/FlowchartWorkspace.vue first-project-web/tests/flowchartStarterContract.test.ts
git commit -m "feat: add guided flowchart starter"
```

## Task 3: Create the node-library panel

**Files:**
- Create: `first-project-web/src/components/flowcharts/NodeLibraryPanel.vue`
- Modify: `first-project-web/src/components/flowcharts/FlowchartWorkspace.vue`
- Modify: `first-project-web/src/utils/flowchartEditorState.ts`
- Modify: `first-project-web/tests/flowchartEditorState.test.ts`
- Create: `first-project-web/tests/flowchartWorkbenchContract.test.ts`

**Interfaces:**
- Props: `collapsed: boolean`, `recentKinds: FlowNodeKind[]`.
- Emits: `create(kind)`, `drag-start(kind, event)`, `toggle`.
- Workspace persists `FlowchartWorkbenchPreferences` under a user/workspace-specific `devnest-flowchart-workbench` key; canvas remains the only component creating X6 cells.

- [ ] **Step 1: Add failing contracts**

```ts
test('node library offers search, categories, click creation, drag creation and collapse', () => {
  assert.match(source, /@click="emit\\('create', shape\\.kind\\)"/)
  assert.match(source, /@dragstart="emit\\('drag-start', shape\\.kind, \\$event\\)"/)
  assert.match(source, /aria-label="折叠节点库"/)
})
```

- [ ] **Step 2: Run focused contracts**

Run: `npm test -- --test-name-pattern="node library"`

Expected: FAIL because the panel is missing.

- [ ] **Step 3: Implement library behavior**

Use `FLOWCHART_SHAPES` and `filterFlowchartShapes` for categories and search. Render a recent section only when `visibleRecentKinds` yields results. Use a native draggable button and write the exact drag payload:

```ts
function dragStart(kind: FlowNodeKind, event: DragEvent) {
  event.dataTransfer?.setData('application/x-devnest-flow-node', kind)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'copy'
  emit('drag-start', kind, event)
}
```

On create and drag start, workspace calls `rememberRecentShape`, guarded localStorage and `canvas.add(kind)` or `canvas.startDrag(kind, event)`.

- [ ] **Step 4: Run state and library tests**

Run: `npm test -- --test-name-pattern="node library|recent shapes|shape palette"`

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add first-project-web/src/components/flowcharts/NodeLibraryPanel.vue first-project-web/src/components/flowcharts/FlowchartWorkspace.vue first-project-web/src/utils/flowchartEditorState.ts first-project-web/tests/flowchartEditorState.test.ts first-project-web/tests/flowchartWorkbenchContract.test.ts
git commit -m "feat: add flowchart node library panel"
```

## Task 4: Replace the property panel with a state-aware inspector

**Files:**
- Create: `first-project-web/src/components/flowcharts/InspectorPanel.vue`
- Delete: `first-project-web/src/components/flowcharts/FlowchartProperties.vue`
- Modify: `first-project-web/src/components/flowcharts/FlowchartWorkspace.vue`
- Modify: `first-project-web/tests/flowchartWorkbenchContract.test.ts`

**Interfaces:**
- Props: `selected: FlowNode | FlowEdge | null`, `nodes: FlowNode[]`, `edges: FlowEdge[]`, `collapsed: boolean`.
- Emits: `change(cell)`, `action(name)`, `select(id)`, `toggle`.
- Node and edge fields preserve the current persisted property semantics.

- [ ] **Step 1: Add failing inspector contracts**

```ts
test('inspector has canvas help, node, edge and collapsed states', () => {
  assert.match(source, /v-if="!selected"/)
  assert.match(source, /v-else-if="'width' in selected"/)
  assert.match(source, /aria-label="检查器"/)
})
test('inspector exposes typed updates and a collapse action', () => {
  assert.match(source, /change: \\[cell: FlowNode \\| FlowEdge\\]/)
  assert.match(source, /emit\\('toggle'\\)/)
})
```

- [ ] **Step 2: Run focused contracts**

Run: `npm test -- --test-name-pattern="inspector has|inspector exposes"`

Expected: FAIL because the component is missing.

- [ ] **Step 3: Implement inspector**

Build a sticky header with `PanelRightClose`. Render canvas help when no object is selected, node fields when `'width' in selected`, edge fields otherwise, and a narrow collapse rail when `collapsed`. Clone values before emitting updates:

```ts
function update(key: string, value: unknown, style = false) {
  if (!props.selected) return
  const next = structuredClone(props.selected)
  if (style) Object.assign(next.style, { [key]: value })
  else Object.assign(next, { [key]: value })
  emit('change', next)
}
```

Retain label, text, color, line, geometry, arrow and edge-kind fields. Keep visible labels, focus rings and a separate destructive delete control.

- [ ] **Step 4: Replace imports, then delete the old file**

Ensure `FlowchartWorkspace` imports only `InspectorPanel`; remove `FlowchartProperties.vue` only after TypeScript succeeds.

- [ ] **Step 5: Run focused tests and build**

Run: `npm test -- --test-name-pattern="inspector|properties"`

Run: `npm run build`

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add first-project-web/src/components/flowcharts/InspectorPanel.vue first-project-web/src/components/flowcharts/FlowchartWorkspace.vue first-project-web/tests/flowchartWorkbenchContract.test.ts
git rm first-project-web/src/components/flowcharts/FlowchartProperties.vue
git commit -m "feat: replace flowchart properties with inspector"
```

## Task 5: Rebuild the canvas chrome while retaining X6 and serialization

**Files:**
- Modify: `first-project-web/src/components/flowcharts/FlowchartCanvas.vue`
- Modify: `first-project-web/src/utils/flowchartAdapter.ts`
- Modify: `first-project-web/tests/flowchartCanvasContract.test.ts`
- Modify: `first-project-web/tests/flowchartEditor.test.ts`

**Interfaces:**
- Canvas receives panel-collapse state only to recompute visible bounds.
- Canvas keeps `change(diagram)`, `selection(cell)`, `toggle-properties`, `edit-selected` emits and its existing public methods.
- `exportImage(kind, title)` hides all editor overlays before X6 export and restores them in `finally`.

- [ ] **Step 1: Add failing canvas contracts**

```ts
test('canvas keeps document actions out of the viewport toolbar', () => {
  assert.match(source, /aria-label="画布工具"/)
  assert.doesNotMatch(source, /分享流程图/)
  assert.doesNotMatch(source, /历史版本/)
})
test('image export hides workbench overlays and restores them', () => {
  assert.match(source, /exportingOverlay\\.value = true/)
  assert.match(source, /finally \\{ exportingOverlay\\.value = false/)
})
```

- [ ] **Step 2: Run the contracts**

Run: `npm test -- --test-name-pattern="document actions|hides workbench"`

Expected: FAIL because the export overlay state is absent.

- [ ] **Step 3: Rewrite canvas layout only**

Keep X6 initialization, adapter calls, history batches and `flowchartEdgeEditor.ts`. Replace current chrome with grouped select/hand, undo/redo, zoom, fit, grid and minimap buttons. Keep the object-anchored command bar for copy, paste, ordering and delete. Use a `ResizeObserver` and next tick to keep selected cells visible when the shell changes panel width.

- [ ] **Step 4: Preserve and isolate the edge editor**

Keep custom edge handles, click insertion, double-click deletion, axis-locked segment dragging, pointer capture, keyboard nudge and Esc restore. Gate toolbar, context bar, hint, minimap and edge layer by `!exportingOverlay`, while the graph stays mounted.

- [ ] **Step 5: Run canvas and geometry tests**

Run: `npm test -- --test-name-pattern="orthogonal|edge drag|canvas|export"`

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add first-project-web/src/components/flowcharts/FlowchartCanvas.vue first-project-web/src/utils/flowchartAdapter.ts first-project-web/tests/flowchartCanvasContract.test.ts first-project-web/tests/flowchartEditor.test.ts
git commit -m "feat: rebuild flowchart canvas chrome"
```

## Task 6: Compose the three-column editor shell

**Files:**
- Create: `first-project-web/src/components/flowcharts/FlowchartEditorShell.vue`
- Modify: `first-project-web/src/components/flowcharts/FlowchartWorkspace.vue`
- Modify: `first-project-web/tests/flowchartWorkbenchContract.test.ts`

**Interfaces:**
- Shell props: `draft`, `statusLabel`, `statusTone`, `selected`, `preferences`, `readOnly`.
- Shell emits title, panel toggles, canvas changes, selection, canvas actions, library creation, manual save, template/history/export/share requests and close.
- Shell exposes `canvas` so workspace retains `CanvasHandle` for save, export and leave handling.

- [ ] **Step 1: Write failing composition contracts**

```ts
test('editor shell owns three-column composition', () => {
  assert.match(shellSource, /class="flowchart-editor-shell"/)
  assert.match(shellSource, /<NodeLibraryPanel/)
  assert.match(shellSource, /<FlowchartCanvas/)
  assert.match(shellSource, /<InspectorPanel/)
})
test('workspace retains persistence but delegates visual composition', () => {
  assert.match(workspaceSource, /<FlowchartEditorShell/)
  assert.match(workspaceSource, /new FlowchartSession/)
  assert.doesNotMatch(workspaceSource, /class="flow-palette"/)
})
```

- [ ] **Step 2: Run composition contracts**

Run: `npm test -- --test-name-pattern="three-column composition|delegates visual"`

Expected: FAIL because the shell is missing.

- [ ] **Step 3: Implement desktop grid and header**

Use `grid-template-columns: var(--library-width) minmax(0, 1fr) var(--inspector-width)`; use 44px rails when a panel is collapsed. Header owns back, title, saving live region, templates, history, export and share. Use 150–200ms width/opacity transition with a reduced-motion override. Do not add a mobile breakpoint that rearranges editor columns.

- [ ] **Step 4: Move layout from workspace to shell**

Move palette, header, panel-transition and canvas markup out of `FlowchartWorkspace`. Keep workspace dialogs, session lifecycle and network calls. Forward all typed events, and expose the canvas ref through `defineExpose({ canvas })`.

- [ ] **Step 5: Run contracts and build**

Run: `npm test -- --test-name-pattern="three-column composition|delegates visual|flowchart workspace"`

Run: `npm run build`

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add first-project-web/src/components/flowcharts/FlowchartEditorShell.vue first-project-web/src/components/flowcharts/FlowchartWorkspace.vue first-project-web/tests/flowchartWorkbenchContract.test.ts
git commit -m "feat: compose three-column flowchart workbench"
```

## Task 7: Route new flowchart creation through starter and delete legacy modal

**Files:**
- Modify: `first-project-web/src/views/KnowledgeView.vue`
- Modify: `first-project-web/tests/flowchartWorkspaceContract.test.ts`
- Modify: `first-project-web/tests/ui/notifications-editor.ts`

**Interfaces:**
- `openCreate('flowcharts')` mounts the workspace with no `documentId`; starter is then the first view.
- `openFlowchart(entry)` opens existing documents directly and bypasses starter.
- Imported file drafts enter the workbench directly after successful parse.

- [ ] **Step 1: Add failing knowledge-entry contract**

```ts
test('knowledge view delegates new flowchart choices to the workspace starter', () => {
  assert.match(source, /openCreate\\('flowcharts'\\)/)
  assert.doesNotMatch(source, /showCanvasStarter/)
  assert.doesNotMatch(source, /title="新建流程图"/)
})
```

- [ ] **Step 2: Run focused contract**

Run: `npm test -- --test-name-pattern="delegates new flowchart"`

Expected: FAIL because legacy starter-modal state remains.

- [ ] **Step 3: Remove only the obsolete creation modal**

Delete `showCanvasStarter` and its blank/template dialog markup/styles. Keep menu and empty-state buttons; make them open the workspace. Retain list-level `.flowchart.json` import behavior and bypass starter for an already parsed imported draft.

- [ ] **Step 4: Add deterministic UI hooks**

Extend isolated fixture controls to open a new flowchart, choose blank, choose a named template and close starter. Do not invoke browser file dialogs or real network calls.

- [ ] **Step 5: Run focused tests and commit**

Run: `npm test -- --test-name-pattern="starter|delegates new flowchart"`

Expected: PASS.

```powershell
git add first-project-web/src/views/KnowledgeView.vue first-project-web/tests/flowchartWorkspaceContract.test.ts first-project-web/tests/ui/notifications-editor.ts
git commit -m "feat: route flowchart creation through starter"
```

## Task 8: Complete resilience and desktop regression coverage

**Files:**
- Modify: `first-project-web/tests/flowchartSession.test.ts`
- Modify: `first-project-web/tests/flowchartDrafts.test.ts`
- Modify: `first-project-web/tests/ui/flowchart-fixture.ts`
- Modify: `first-project-web/tests/ui/flowchart-fixture.test.ts`
- Modify: `first-project-web/tests/ui/notifications-editor.ts`
- Modify: `first-project-web/tests/ui/notifications-editor.html`

**Interfaces:**
- `FlowchartSession` retains its current save queue, dirty state and conflict behavior.
- Fixture scenarios remain `flowchart-offline`, `flowchart-conflict` and `flowchart-create-retry`, with starter-specific deterministic controls.

- [ ] **Step 1: Add failing resilience coverage**

```ts
test('a starter-created draft remains dirty after failed first cloud save', async () => {
  const initial = { title: '新流程', domainId: null, favorite: false, diagram: createTemplate('sequence') }
  const session = new FlowchartSession(initial, null, 'new-flowchart', {
    create: async () => { throw new Error('offline') },
    update: async (_id, input) => saved(input),
  })
  await assert.rejects(session.save('MANUAL'), /offline/)
  assert.equal(session.dirty, true)
  assert.equal(session.status, 'error')
  assert.deepEqual(session.draft.diagram, initial.diagram)
})
test('every starter template remains schema-valid', () => {
  for (const kind of ['sequence', 'decision', 'architecture'] as const) assert.doesNotThrow(() => validateDiagram(createTemplate(kind)))
})
```

- [ ] **Step 2: Run focused resilience tests**

Run: `npm test -- --test-name-pattern="starter-created|starter template"`

Expected: FAIL until fixture/session coverage is added, then PASS.

- [ ] **Step 3: Extend isolated UI regression**

At 1280px and 1440px in light and dark modes, verify starter blank/template/close paths; node-library search and click create; both panel collapses; inspector state changes; Ctrl/Cmd+S, undo/redo and Delete; edge insert/remove/Esc; autosave retry/conflict; history; export; and reopening a saved old document. Assert exported SVG omits `flow-canvas__edge-editor`, toolbars, context bars and inspector DOM.

- [ ] **Step 4: Run all verification commands**

Run: `npm test`

Run: `npm run build`

Run: `npm run test:ui:build`

Expected: all exit 0.

- [ ] **Step 5: Browser regression and final commit**

Follow `first-project-web/tests/ui/README.md` with the isolated fixture. Record browser-console state and desktop overflow checks in the handoff.

```powershell
git add first-project-web/tests/flowchartSession.test.ts first-project-web/tests/flowchartDrafts.test.ts first-project-web/tests/ui/flowchart-fixture.ts first-project-web/tests/ui/flowchart-fixture.test.ts first-project-web/tests/ui/notifications-editor.ts first-project-web/tests/ui/notifications-editor.html
git commit -m "test: cover desktop flowchart workbench"
```

## Final Integration Check

- [ ] Verify only files in this plan are staged; preserve unrelated working-tree changes.
- [ ] Run `git diff --check` with no reported whitespace errors.
- [ ] Re-run `npm test`, `npm run build`, and `npm run test:ui:build` after the final commit.
- [ ] Confirm the starter is the first view for new documents and existing documents bypass it.
- [ ] Confirm legacy diagrams open, edit, save, restore history, import, export and share without API or schema changes.
