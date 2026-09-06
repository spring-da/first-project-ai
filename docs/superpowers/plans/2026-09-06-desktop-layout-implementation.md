# DevNest Desktop Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve DevNest's PC layout density, content width, visual consistency, and keyboard accessibility without changing business behavior or mobile-specific layouts.

**Architecture:** Add shared desktop layout tokens and semantic content tracks in the global stylesheet, then make narrowly scoped page-level adjustments where content density differs. Preserve full-width editor/tool workspaces and keep all API, Pinia, routing, authorization, and persistence behavior unchanged.

**Tech Stack:** Vue 3.5, TypeScript 6, Vite 8, scoped CSS, Lucide Vue icons

**Spec:** `docs/superpowers/specs/2026-09-06-desktop-layout-design.md`

## Global Constraints

- Target only PC Web; retain existing mobile media queries without adding mobile redesign work.
- Preserve the existing blue theme, system font stack, Lucide icons, light/dark themes, routes, APIs, stores, and business behavior.
- Standard content max width is 1600px; focused form content max width is 1280px; wide workspaces use 1840px or the full available width.
- Keep Markdown editing and system tools full width and full height.
- Do not add dependencies, backend changes, data migrations, or speculative abstractions.
- Do not add implementation-mirroring tests for reversible CSS changes; use the existing test suite, type/build checks, and isolated UI fixture.

---

### Task 1: Shared desktop layout and visual tokens

**Files:**
- Modify: `first-project-web/src/style.css`

**Interfaces:**
- Consumes: existing theme tokens and `.page-content` shared layout class.
- Produces: `--content-standard`, `--content-focused`, `--content-wide`, desktop gutters and semantic `.page-content--focused` / `.page-content--wide` tracks.

- [ ] **Step 1: Add semantic desktop tokens**

Add layout and spacing variables beside the existing radius and typography tokens:

```css
--content-standard: 1600px;
--content-focused: 1280px;
--content-wide: 1840px;
--page-gutter: clamp(24px, 2vw, 40px);
--section-gap: clamp(16px, 1vw, 20px);
--panel-padding: clamp(20px, 1.25vw, 24px);
```

- [ ] **Step 2: Apply the standard content track**

Replace the unlimited shared page width with a centered width that includes its gutter:

```css
.page-content {
  width: min(100%, calc(var(--content-standard) + (var(--page-gutter) * 2)));
  max-width: none;
  margin-inline: auto;
  padding: 32px var(--page-gutter) 64px;
}
.page-content--focused { --content-standard: var(--content-focused); }
.page-content--wide { --content-standard: var(--content-wide); }
```

Keep `.writing-mode` and full-height tool/editor exceptions able to occupy all available space.

- [ ] **Step 3: Remove the collapsed-sidebar width override**

Delete the desktop rule that changes all non-writing `.page-content` elements to `max-width: none` when the sidebar collapses. Sidebar state must not change the content measure.

- [ ] **Step 4: Normalize shared icon and metadata rendering**

Ensure Lucide SVGs inside grid/flex icon frames render as blocks, numeric metrics use tabular figures, and shared 12px metadata remains legible in both themes.

- [ ] **Step 5: Verify the shared stylesheet compiles**

Run: `npm run build`

Expected: Vue type checking and Vite production build exit with code 0.

### Task 2: App shell and page header alignment

**Files:**
- Modify: `first-project-web/src/layouts/AppLayout.vue`
- Modify: `first-project-web/src/components/PageHeader.vue`

**Interfaces:**
- Consumes: shared desktop gutter and content-width tokens from Task 1.
- Produces: stable PC shell alignment, a focusable main content target, and a consistent page heading block.

- [ ] **Step 1: Align topbar content rhythm**

Use the shared gutter token for topbar horizontal padding and keep the breadcrumb, search, and global controls on one stable desktop baseline.

- [ ] **Step 2: Add a route focus target**

Give `<main class="workspace">` an id and `tabindex="-1"`. After a real route-name change, focus the main region with `preventScroll: true` so keyboard users reach the new page without changing the visible scroll position.

- [ ] **Step 3: Mark repeated decorative icons correctly**

Add `aria-hidden="true"` to icons that sit beside equivalent visible text. Keep `aria-label` on icon-only buttons and preserve current expanded/selected states.

- [ ] **Step 4: Normalize page header spacing**

Use a fixed desktop margin relationship between `PageHeader`, its description, and action slot. Keep headings at the existing type scale and constrain descriptive copy to a readable measure.

- [ ] **Step 5: Build the shell changes**

Run: `npm run build`

Expected: type checking passes and no route/layout imports are unused.

### Task 3: Compact the dashboard and remove empty fixed-height panels

**Files:**
- Modify: `first-project-web/src/views/DashboardView.vue`
- Modify: `first-project-web/src/style.css`

**Interfaces:**
- Consumes: `.metric-grid`, `.metric-card`, `.dashboard-grid`, `.panel`, and semantic color tokens.
- Produces: four compact metric cards, content-driven two-column panels, and consistent knowledge cards.

- [ ] **Step 1: Replace hard-coded dashboard colors**

Use `var(--violet)` for snippets and `var(--warning)` for Markdown documents. Keep tasks on `var(--accent)` and projects on `var(--success)`.

- [ ] **Step 2: Reduce metric-card height and decoration**

Lower the metric minimum height to approximately 120–136px, reduce the decorative background circle, and keep hover feedback subtle.

- [ ] **Step 3: Remove the viewport-based panel height**

Replace `height: clamp(430px, 52svh, 560px)` with a compact content-driven minimum around 320–360px. Remove mandatory internal scrolling from `.dashboard-scroll`; allow the page to grow with longer content.

- [ ] **Step 4: Keep empty states proportionate**

Ensure an empty current-project panel fills the compact panel without creating an additional viewport-height blank region.

- [ ] **Step 5: Run focused dashboard logic tests**

Run: `npm test`

Expected: task filtering, dashboard links, sorting, workspace isolation, and all other existing tests pass.

### Task 4: Apply semantic content tracks and page-level density

**Files:**
- Modify: `first-project-web/src/views/ProjectsView.vue`
- Modify: `first-project-web/src/views/KnowledgeView.vue`
- Modify: `first-project-web/src/views/AnnouncementsView.vue`
- Modify: `first-project-web/src/views/MessageCenterView.vue`
- Modify: `first-project-web/src/views/ToolsView.vue`
- Modify: `first-project-web/src/views/AdminAccountsView.vue`
- Modify: `first-project-web/src/views/ProfileView.vue`

**Interfaces:**
- Consumes: semantic page tracks and panel spacing from Task 1.
- Produces: consistent standard, focused, and wide desktop page layouts without changing page data flows.

- [ ] **Step 1: Assign page tracks**

Add `page-content--wide` to the knowledge browsing page and keep writing mode unrestricted. Keep tools full width through its existing flex workspace. Add `page-content--focused` to profile. Standard pages use the default track.

- [ ] **Step 2: Remove conflicting unlimited-width declarations**

Delete `max-width: none` from standard page selectors such as `.admin-page`. Retain it only for explicit editor/full-workspace states.

- [ ] **Step 3: Normalize standard page section gaps**

Use `var(--section-gap)` between project, announcement, message, admin, and profile sections. Use `var(--panel-padding)` for their major panel interiors where an existing special layout does not require another value.

- [ ] **Step 4: Improve desktop text measure and row scanning**

Constrain long descriptions, message bodies, and form copy to readable widths. Raise important 9px/10px metadata to at least 11–12px while keeping compact count badges small.

- [ ] **Step 5: Preserve special workspaces**

Confirm Markdown editing, knowledge reading, JSON editing, CRON tools, image loading, audit records, and communication scrolling retain their current functional CSS states.

- [ ] **Step 6: Build all page changes**

Run: `npm run build`

Expected: production build succeeds with all scoped CSS and Vue templates accepted.

### Task 5: Accessibility and consistency audit

**Files:**
- Modify only the files from Tasks 1–4 where an observed issue remains.

**Interfaces:**
- Consumes: final markup and CSS from Tasks 1–4.
- Produces: visible focus states, semantic icon treatment, light/dark parity, and no layout-changing hover state.

- [ ] **Step 1: Search for remaining visual literals and tiny text**

Run:

```powershell
rg -n "font-size:\s*(8|9|10)px|#[0-9a-fA-F]{6}" first-project-web/src/views first-project-web/src/components first-project-web/src/style.css
```

Review each match. Keep syntax-highlighting colors and justified compact badges; replace page-specific semantic colors and important metadata literals.

- [ ] **Step 2: Search icon-only controls**

Run:

```powershell
rg -n "<button[^>]*>|class=\"icon" first-project-web/src --glob "*.vue"
```

For every icon-only control touched by this plan, confirm it has `aria-label` or equivalent visible text and a keyboard focus style.

- [ ] **Step 3: Confirm reduced-motion support**

Retain the existing global `prefers-reduced-motion: reduce` rule and ensure new transitions use shared motion tokens.

- [ ] **Step 4: Check the final diff**

Run: `git diff --check`

Expected: no whitespace errors.

### Task 6: Full verification and delivery

**Files:**
- Modify: `docs/superpowers/plans/2026-09-06-desktop-layout-implementation.md` only to mark completed checkboxes.

**Interfaces:**
- Consumes: all code changes from Tasks 1–5.
- Produces: fresh evidence that the implementation is buildable and the isolated UI fixture remains available.

- [ ] **Step 1: Run the complete logic suite**

Run: `npm test`

Expected: all tests pass with zero failures.

- [ ] **Step 2: Run the production build**

Run: `npm run build`

Expected: Vue type checking and Vite production build complete with exit code 0.

- [ ] **Step 3: Build the isolated UI fixture**

Run: `npm run test:ui:build`

Expected: fixture type checking and build complete with exit code 0.

- [ ] **Step 4: Inspect desktop fixture scenarios**

Use the isolated page described in `first-project-web/tests/ui/README.md`. Check dashboard, projects, knowledge, announcements, messages, tools, admin, and profile at 1280px, 1440px, and 1920px in light and dark themes. Confirm standard tracks are centered, full-width workspaces remain full width, panels do not create excessive blank space, and no horizontal overflow appears.

- [ ] **Step 5: Commit the implementation**

```powershell
git add first-project-web/src docs/superpowers/plans/2026-09-06-desktop-layout-implementation.md
git commit -m "feat: refine desktop workspace layout"
```
