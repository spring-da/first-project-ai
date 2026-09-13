# 开发日志替换为流程图

## Approved scope

Replace development logs everywhere with a first-class standard flowchart editor. Preserve articles and snippets and the existing uncommitted sharing/workspace changes. Delete all legacy logs including trash and log sharing records through a new Flyway migration after a recoverable backup; do not run this migration against a real user database during development. Historical migrations and audit events remain intact.

Use Vue 3, AntV X6 exactly 3.1.8, Spring Boot, PostgreSQL. Desktop editing; mobile read-only canvas and normal knowledge management. One canvas per item. Shapes: terminal, rectangle, rounded rectangle, diamond, input/output, database, text. Lines: straight/orthogonal, ports, arrows, labels, editable vertices. Include selection, movement, resizing, copy/paste, delete, undo/redo, snapping, z order, fill/stroke/text styling, pan/zoom/fit, grid, minimap, fullscreen and templates (sequence, decision, architecture). No images, arbitrary HTML, external asset URLs, collaboration, AI, swimlanes, grouping, multipage or automatic layout.

Integrate folders, favorites, unified lists and search, bulk move/delete, trash, history, shared pool and public bundles. Lazy-load engine and diagram bodies. Export current unsaved diagram as PNG/SVG (white background, no grid/editor controls), and editable .flowchart.json. Import creates a new document in the selected folder.

Autosave after 2 seconds idle, at most 15 seconds between submissions while editing. Manual save shortcut. Serial saves must preserve edits made during requests. IndexedDB drafts isolated by actor, workspace owner, document and session. Offline/retry/rate limiting retains drafts; concurrent version conflicts stop autosave and offer reload or save a copy. Creation retries idempotent. Cloud history: automatic checkpoint at most every 5 minutes, changed manual save/create/restore checkpoints, no duplicate identical snapshots, latest 100, pages of 20. Local viewport/selection are not cloud edits. Shares show latest saved content; trash invalidates old shares permanently, even after restore.

## Contract (shared implementation authority)

- Diagram: `{schemaVersion:1,nodes:FlowNode[],edges:FlowEdge[]}`.
- FlowNode: `{id,kind,label,x,y,width,height,zIndex,style}`. Kind = `terminal|rectangle|rounded|diamond|io|database|text`.
- Node style: `{fill,stroke,strokeWidth,dash,textColor,fontSize,textAlign}`; textAlign = `left|center|right`; dash boolean; colors `#RRGGBB` or `#RRGGBBAA`, fill also `transparent`.
- FlowEdge: `{id,kind,source,target,label,vertices,zIndex,sourceArrow,targetArrow,style}`. Kind = `straight|orthogonal`; endpoints `{nodeId,port}` with port `top|right|bottom|left`; vertices `{x,y}[]`.
- Edge style: `{stroke,strokeWidth,dash,textColor,fontSize}`.
- All model properties required; reject unknown properties, arbitrary engine attributes, markup, external-resource configuration. Labels are plain text (HTML-looking text stays literal). IDs 1–80 ASCII letters/digits/underscore/hyphen, unique across cells. Endpoint IDs must exist. Coordinates -1,000,000..1,000,000, dimensions 10..10,000, fontSize 8..96, strokeWidth 0..12, zIndex integer -10,000..10,000, labels max 10,000 characters, vertices max 100 per edge. Finite numbers only. Max diagram serialized UTF-8 5 MiB, 1,000 nodes, 2,000 edges. Cycles and self-loops allowed, empty diagram allowed.
- Draft: `{title,domainId,favorite,diagram}`; title trimmed/nonblank <=200. Create adds UUID `creationKey`; update adds nonnegative `expectedVersion`, `saveMode:'AUTO'|'MANUAL'` (default MANUAL).
- Summary: `{id,title,domainId,favorite,createdAt,updatedAt,version,deletedAt,nodeCount,edgeCount,excerpt}`. Detail adds `diagram`. Lists are summary arrays, `GET /flowcharts?q=` searches title/node/edge labels and returns summaries only.
- REST relative `/api/v1`: GET/POST `/flowcharts`, GET/PUT/DELETE `/flowcharts/{id}`, GET `/flowcharts/trash`, POST `/{id}/restore`, DELETE `/{id}/permanent`, GET `/{id}/revisions?page=0`, GET `/{id}/revisions/{revisionId}`, POST `/{id}/revisions/{revisionId}/restore` body `{expectedVersion}`. Revision summary `{id,documentVersion,title,action,createdAt}`; detail adds draft fields. Creation returns 201; deletes 204; conflicts 409. `creationKey` unique per owner, repeat returns existing item without changing content (409 if trashed).
- Bulk type `FLOWCHART` requires expectedVersion. Sharing resource type `FLOWCHART`, shared content adds nullable `diagram`; content string is extracted search text, not serialized graph. Other resource fields remain compatible. Existing legacy snippet public links remain valid; legacy log links disappear.
- Export source envelope `{format:'devnest-flowchart',schemaVersion:1,title,diagram}`. Does not carry server IDs/owner/version/favorite/folder. Import validates and creates a fresh item.

## Tasks and verification

1. Backend flowchart validation, persistence, CRUD/history/concurrency, share/bulk/admin integration, log removal and V19 migration. Test actual HTTP/auth, invalid diagrams, idempotency, concurrent versions, history dedup/retention, trash/share generations, mixed bulk rollback. PostgreSQL upgrade fixture contains old logs plus mixed and log-only shares; verify pre-migration backup then V19 and restore rehearsal.
2. Frontend model/adapter, independent save controller and IndexedDB recovery, X6 canvas, workspace editor and reader. Tests first for validator, import roundtrip, serial saves preserving new edits, retries, conflicts and account isolation.
3. Frontend integration: summaries/store/search/type filters/create/preview/trash/bulk/favorite/sharing, dashboard/profile/admin, lazy loading, legacy query handling, fixture changes.
4. Review, full tests/builds, isolated browser checks desktop/mobile/themes and 300-node/500-edge graph, real isolated PostgreSQL new-install/upgrade. Update API/setup/feature documentation and sync completed changes back to original checkout only after baseline-hash checks.

## Execution notes

Feature worktree: `.worktrees/flowcharts`, branch `codex/flowcharts`. Existing source copied without committing user work; manifest `.test-tmp/flowcharts/baseline.json` records starting bytes. Never commit, reset or overwrite unrelated pre-existing changes. Main controller owns frontend; one backend implementer owns backend. Review agents read only.
