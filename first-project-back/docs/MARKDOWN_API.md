# Markdown document API

All owner-management endpoints require the same bearer token as the other
`/api/v1` resources. Every owner operation is scoped to the authenticated JWT
subject; clients never send an `ownerId`. The public share read endpoints described
below intentionally do not require an account.

## Document shape

```json
{
  "id": "8f5f...",
  "title": "Spring Boot notes",
  "fileName": "spring-boot-notes.md",
  "content": "# Spring Boot notes\n\nMarkdown content...",
  "favorite": false,
  "domainId": null,
  "createdAt": "2026-08-25T12:00:00Z",
  "updatedAt": "2026-08-25T12:00:00Z",
  "version": 0
}
```

The list response is intentionally lightweight: it returns `excerpt` and
`contentLength` instead of the complete `content`. Clients request the detail
endpoint only when an article is opened, which keeps workspace startup fast as
the knowledge base grows.

| Operation | Endpoint |
|---|---|
| List | `GET /api/v1/markdown-documents` |
| Detail | `GET /api/v1/markdown-documents/{id}` |
| Create | `POST /api/v1/markdown-documents` |
| Update | `PUT /api/v1/markdown-documents/{id}` |
| Move to trash | `DELETE /api/v1/markdown-documents/{id}` |
| Trash summaries | `GET /api/v1/markdown-documents/trash` |
| Restore from trash | `POST /api/v1/markdown-documents/{id}/restore` |
| Permanently delete a trashed article and its history | `DELETE /api/v1/markdown-documents/{id}/permanent` |
| Revision summaries (20 per page, zero-based) | `GET /api/v1/markdown-documents/{id}/revisions?page=0` |
| Revision detail | `GET /api/v1/markdown-documents/{id}/revisions/{revisionId}` |
| Restore a revision | `POST /api/v1/markdown-documents/{id}/revisions/{revisionId}/restore` |

## Public read-only sharing

An authenticated document owner can create, list and revoke public links:

| Operation | Endpoint |
|---|---|
| Create a link | `POST /api/v1/markdown-documents/{id}/shares` |
| List link metadata | `GET /api/v1/markdown-documents/{id}/shares` |
| Revoke a link | `DELETE /api/v1/markdown-documents/{id}/shares/{shareId}` |

Creation accepts an absolute future expiration time, limited to 365 days:

```json
{"expiresAt":"2026-09-06T12:00:00Z"}
```

The `201` response contains `id`, `token`, `createdAt` and `expiresAt`, and uses
`Cache-Control: no-store`. The 256-bit URL-safe `token` is returned only at creation;
the database stores only its SHA-256 digest and list responses never return it. A
document can have at most 20 simultaneously active links.

Anyone holding an active token can read the latest saved document without a JWT:

```http
GET /api/v1/public/markdown-shares/{token}
GET /api/v1/public/markdown-shares/{token}/images/{imageId}
```

Public responses use `Cache-Control: no-store`. The image endpoint permits only
managed image IDs actually referenced by the shared document. Invalid, expired or
revoked links, links to trashed documents, and unreferenced images return 404. Moving
an article to trash, revoking the link, permanently deleting the article, or deleting
its owner therefore invalidates public access immediately.

Create accepts the document fields above without `id`, timestamps or
`expectedVersion`. `title`, `fileName`, and `content` are required. `domainId` is
optional and must refer to a domain owned by the current user.

Update requires `expectedVersion` from the last fetched article; omitting it or
submitting `null` returns HTTP 400. A stale version returns HTTP 409 and does not
change the article. Create and update intentionally use separate request contracts
so no client can opt out of stale-edit protection.
Revision restores require `{ "expectedVersion": 2 }` and also reject stale versions.

Normal lists, detail reads and exports exclude trashed articles. Trash summaries
include `deletedAt`; there is no automatic expiration or empty-trash operation.
Permanent deletion is allowed only for articles already in the trash, and also
removes all their history. Normal deletes retain article content and snapshots.

Revision summaries include `id`, `documentVersion`, `action`, `title`, `fileName`,
and `createdAt`. Revision detail additionally includes `content`, `domainId` and
`favorite`. Creation/import, changed saves, trash recovery and changed version
restores create snapshots. Identical saves/restores do not duplicate snapshots.
Version numbers may have gaps (e.g. moving to trash increments the document
version). Snapshots are immutable; restoring an old version preserves later
snapshots. A deleted historical folder falls back to unassigned on restore.

### Upgrade and data safety

Deploy the backend before the frontend. Flyway migration
`V5__markdown_recovery_and_history.sql` adds soft deletion and optimistic version
fields, creates the revision table, and copies each existing article's current
content into a `BASELINE` snapshot. Back up the database and allow enough disk
space for this initial copy before upgrading. Existing articles remain active.
The migration cannot recover content deleted or overwritten before this upgrade.
History and trash are retained until the user permanently deletes an article;
include both tables in routine database backups and capacity monitoring.

`V10__add_markdown_share_links.sql` creates the public-link metadata table. It does
not copy article content or store plaintext share tokens, and existing articles stay
private until their owner explicitly creates a link.

## Batch import

```http
POST /api/v1/markdown-documents/import
Content-Type: application/json
```

```json
{
  "documents": [
    {
      "fileName": "first-note.md",
      "title": "Optional explicit title",
      "content": "# First note\n\nHello",
      "domainId": null,
      "favorite": false
    }
  ]
}
```

The response is `{ "importedCount": 1, "documents": [...] }`, where each item is
a complete document including `content`. If `title` is omitted, the service uses
the first H1 heading and then the file stem as fallbacks. Import is transactional.

Limits: 50 documents, 1,000,000 characters per document, and 5,000,000 characters
across one import request.

The browser uses the multipart variant for real files:

```http
POST /api/v1/markdown-documents/import
Content-Type: multipart/form-data

files=<one or more .md/.markdown/.mdown/.zip files>
domainId=<optional target domain>
```

Up to 50 selected files and 50 resulting documents are accepted. A standalone
Markdown file cannot exceed 1 MB. A ZIP cannot exceed 100 MB, may contain at
most 500 entries and expands to at most 120 MB. ZIP paths are normalized and
cannot escape the archive root. Relative PNG/JPEG/GIF/WebP image references in
Markdown are uploaded through the normal OSS image service and rewritten to
stable `/api/v1/markdown-images/{id}` references; remote, absolute and missing
references are left unchanged. The whole import is transactional and uploaded
objects are cleaned up if database persistence rolls back.

When `domainId` is omitted the imported documents are unclassified. The web UI
passes the currently selected real folder; importing while viewing “全部知识” or
“未分类” therefore creates unclassified documents.

When the API is deployed behind Nginx, configure an appropriate request-body
limit (`client_max_body_size 105m;` in this project) or larger ZIP imports may
be rejected by the proxy before they reach the application.

## Batch category move

```http
PATCH /api/v1/markdown-documents/bulk-domain
Content-Type: application/json
```

```json
{
  "documents": [
    { "id": "document-id-1", "expectedVersion": 2 },
    { "id": "document-id-2", "expectedVersion": 5 }
  ],
  "domainId": "target-domain-id"
}
```

Use `null` for `domainId` to move documents to “未分类”. The target domain and
every document must belong to the current user. Up to 100 documents are moved
in one transaction; a stale version or inaccessible document rejects the whole
batch rather than partially moving it.

## Batch export

```http
POST /api/v1/markdown-documents/export
Content-Type: application/json
```

```json
{
  "ids": ["document-id-1", "document-id-2"]
}
```

The response is an `application/zip` attachment containing UTF-8 Markdown files.
An empty `ids` list exports all documents owned by the current user. Export is
limited to 100 documents and 100 MB of uncompressed Markdown plus managed image
content. Managed image references are rewritten to relative `assets/...` paths
and the image bytes are included once in that directory, so the archive can be
read outside DevNest and imported back without losing images. A request fails
as a whole if any selected ID does not belong to the current user.

Imported and exported file names are reduced to a safe base name, normalized to a
`.md`/`.markdown` extension, protected against traversal and Windows reserved
names, and de-duplicated inside the ZIP.


管理员在成员工作区使用同一接口并携带 `X-Workspace-Owner`，服务端逐次验证管理员后按目标成员隔离读写；完整规则见 [API.md](API.md#管理员模拟登录与业务编辑)。文章写入与恢复仍要求正确的 `expectedVersion`。图片上传归属于目标成员，读取响应使用 `Vary: Authorization, X-Workspace-Owner`，客户端缓存也按工作区隔离。

## 共享池与批量集合

文章可通过 `/api/v1/sharing/pool` 发布到登录成员共享池，也可与代码片段、流程图混合创建 `/api/v1/sharing/links` 外部集合；完整请求、响应和撤销规则见 [API.md](API.md#共享池与批量外链)。列表只返回摘要，正文和图片按选中的条目逐次读取。共享正文取最新已保存版本，图片只允许当前正文引用且属于原作者的图片。

文章移入回收站时递增内部分享代次，永久终止此前的共享池条目、集合条目及旧单篇链接。回收站恢复或历史恢复不会降低代次，也不会重新公开已结束的分享；恢复后必须主动重新发布。作者禁用或删除后，所有公开读取立即不可用。旧单篇接口继续保留。
