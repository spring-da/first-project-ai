# DevNest REST API

Base URL：`http://localhost:8080/api/v1`

除注册、登录、昵称可用性检查和健康检查外，所有接口都需要：

```http
Authorization: Bearer <accessToken>
Content-Type: application/json
```

## 认证

### 注册

```http
POST /auth/register
```

```json
{
  "invitationToken": "管理员一次性邀请链接中的 Token",
  "password": "请使用至少8位密码",
  "displayName": "springda"
}
```

注册采用一次性邀请 Token：管理员通过 `POST /admin/invitations` 添加邮箱后，将响应中的 Token 组成邀请链接并通过可信渠道交给用户。Token 默认 48 小时有效、只能成功使用一次，数据库只保存 SHA-256 摘要。注册接口不接收邮箱，而是从 Token 对应的邀请中确定邮箱；无效、过期、已使用以及对应邮箱已经注册都统一返回 `400 Bad Request` 和相同提示，避免公开枚举邀请及账号状态。昵称会执行 Unicode 规范化、去除首尾空白并忽略大小写查重；冲突返回 `409 Conflict`。

注册页可在提交前检查昵称：

```http
GET /auth/display-name-availability?displayName=springda
```

响应为 `{ "available": true }`。该结果只用于即时提示，注册事务仍会再次检查唯一约束，避免并发抢占。

### 登录

```http
POST /auth/login
```

```json
{
  "email": "springda@example.com",
  "password": "你的密码"
}
```

响应中的 `accessToken` 用于后续请求。用户摘要同时返回权限与强制改密状态：

```json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresInSeconds": 43200,
  "user": {
    "id": "用户 UUID",
    "email": "springda@example.com",
    "displayName": "springda",
    "role": "USER",
    "mustChangePassword": false
  }
}
```

`role` 为 `USER` 或 `ADMIN`。V7 数据库迁移会将已注册的 `springda0099@gmail.com` 升级为 `ADMIN`。

登录按“规范化邮箱 + 来源地址”限流，默认每分钟最多 10 次；已存在账号连续 5 次密码失败后锁定 15 分钟。未知邮箱、密码错误、账号禁用、账号锁定和临时密码过期均返回相同的 `401` 认证失败信息。

### 当前账户

```http
GET /auth/me
```

返回最新用户摘要。Web 客户端启动时使用该接口刷新角色、启用状态与强制改密状态，避免依赖旧的本机会话数据。

### 修改密码

```http
PUT /auth/password
```

```json
{
  "currentPassword": "当前密码",
  "newPassword": "至少8位的新密码"
}
```

修改成功后返回新的登录响应和 JWT，并使修改前签发的 JWT 全部失效。管理员重置后会生成只显示一次的 20 位随机临时密码，默认 30 分钟过期；用户使用临时密码登录后只能访问 `GET /auth/me` 和 `PUT /auth/password`，其他接口返回带有 `code: PASSWORD_CHANGE_REQUIRED` 的 `403`，直至完成改密。临时密码过期后必须由管理员重新重置。

### 退出全部设备

```http
POST /auth/logout-all
```

返回 `204 No Content`，并递增账户的 `authVersion`，使当前浏览器及其他设备此前签发的全部 JWT 立即失效。注册、登录、当前账户、修改密码和注销响应均设置 `Cache-Control: no-store`。

## 管理员接口

以下接口仅允许 `ADMIN`。服务端会在每次受保护请求中重新读取账户状态与角色，因此禁用、删除和权限变更不会等待旧 JWT 过期才生效。

| 功能 | 接口 | 响应 |
|---|---|---|
| 查看已注册账号与待注册邮箱 | `GET /admin/accounts` | `200` 人员列表 |
| 创建一次性注册邀请 | `POST /admin/invitations` | `201` 待注册记录、一次性 Token 和过期时间 |
| 轮换邀请 Token | `POST /admin/invitations/{invitationId}/rotate-token` | `200` 新的一次性 Token 和过期时间 |
| 撤销待注册邮箱 | `DELETE /admin/invitations/{invitationId}` | `204` |
| 启用或禁用账号 | `PATCH /admin/accounts/{userId}/status` | `200` 更新后的账号 |
| 重置用户密码 | `POST /admin/accounts/{userId}/reset-password` | `200` 一次性临时密码和过期时间 |
| 永久删除账号 | `DELETE /admin/accounts/{userId}` | `204` |
| 查看管理员操作记录 | `GET /admin/audit-events?limit=40` | `200` 最近 1–100 条记录 |
| 查看公告管理记录 | `GET /admin/announcements?limit=20` | `200`，包含已撤回公告与已读人数 |
| 发布系统公告 | `POST /admin/announcements` | `201` 新公告 |
| 撤回系统公告 | `DELETE /admin/announcements/{announcementId}` | `204` |
| 只读查看单个用户工作区 | `GET /admin/accounts/{userId}/workspace` | `200` 用户资料及分模块数据 |
| 只读查看用户 Markdown 正文 | `GET /admin/accounts/{userId}/workspace/markdown-documents/{documentId}` | `200` |
| 只读读取用户 Markdown 图片 | `GET /admin/accounts/{userId}/workspace/markdown-images/{imageId}` | `200` 图片字节 |

添加邮箱：

```json
{
  "email": "friend@example.com"
}
```

创建及轮换邀请的响应都包含只显示一次的密钥，并设置 `Cache-Control: no-store`：

```json
{
  "account": {
    "invitationId": "邀请 UUID",
    "email": "friend@example.com",
    "registered": false,
    "invitationExpiresAt": "2026-08-31T12:00:00Z",
    "invitationExpired": false
  },
  "invitationToken": "只显示一次的随机 Token",
  "expiresAt": "2026-08-31T12:00:00Z"
}
```

重置密码响应同样设置 `Cache-Control: no-store`：

```json
{
  "temporaryPassword": "只显示一次的随机临时密码",
  "expiresAt": "2026-08-29T12:30:00Z"
}
```

修改启用状态：

```json
{
  "enabled": false
}
```

JWT 包含用户当前的 `authVersion`。禁用、重置密码和修改密码都会递增版本，因此操作前签发的全部 JWT 会立即失效。重置密码还会设置 `mustChangePassword=true`。永久删除会通过现有 PostgreSQL 外键级联删除该账号的工作区数据，并撤销其注册资格；若以后需要重新注册，管理员必须再次添加该邮箱。管理员账号不能被禁用、重置或删除。

账号邀请、启用、禁用、密码重置、永久删除，以及管理员通过 `X-Workspace-Owner` 发起的成员数据写操作都会保存审计事件。事件仅包含管理员邮箱、目标、资源类型、HTTP 方法、结果和时间，不记录密码、邀请 Token 或成员正文；列表只允许管理员读取并设置 `Cache-Control: no-store`。

## 系统公告

管理员发布公告时提交，`content` 使用 Markdown：

```json
{
  "title": "知识库功能升级",
  "content": "## 本次更新\n\n- 增加历史恢复\n- 优化意见交流图片加载\n\n[查看使用说明](https://example.com/docs)"
}
```

成员使用 `GET /announcements/unread` 获取尚未阅读的有效公告。Web 客户端使用与知识库一致的安全渲染器展示标题、列表、引用、代码、表格和链接，原始 HTML 会被转义。进入登录后的主界面时自动读取并逐条弹出；用户确认后调用 `POST /announcements/{announcementId}/read`，服务端为当前真实登录账号保存已读状态，之后登录不再重复提示。`GET /announcements?limit=10` 返回有效公告历史。管理员账号不显示成员公告弹窗；撤回公告后，尚未阅读的成员也不会再收到该公告。

## 意见交流

意见区对所有已登录账号开放，使用真实登录身份，不受管理员模拟成员工作区的 `X-Workspace-Owner` 影响。

| 功能 | 接口 | 说明 |
|---|---|---|
| 滚动读取主题消息 | `GET /community/messages?size=8&cursor={messageId}` | 按发布时间倒序，响应包含 `items` 与下一页 `nextCursor` |
| 读取回复 | `GET /community/messages/{messageId}/replies?page=0&size=10` | 展开回复时调用，按发布时间正序 |
| 发布主题 | `POST /community/messages` | `multipart/form-data`，`content` 与 `image` 至少提供一个 |
| 发布回复 | `POST /community/messages/{messageId}/replies` | 与主题使用相同的 multipart 字段 |
| 读取图片 | `GET /community/messages/{messageId}/image` | Bearer Token 鉴权后的图片字节 |
| 删除消息 | `DELETE /community/messages/{messageId}` | 作者可删除自己的消息，管理员可管理任意消息 |

正文最长 2000 字符。图片最大 20 MB，只接受根据文件字节识别出的 PNG、JPG、GIF 或 WebP，不接受 SVG；对象保存在现有私有 OSS 下，消息响应返回相对于 API Base URL 的稳定地址，例如 `/community/messages/{id}/image`，客户端读取时携带 Bearer Token。删除主题会同时删除其回复，数据库事务提交后清理对应 OSS 对象。主题列表默认加载 8 条并使用游标继续加载，回复默认折叠，仅在用户展开后请求。响应还包含作者的 `authorAvatarUrl`；Web 客户端会把 HTTP(S) 链接渲染为安全的可点击链接，在图片接近可视区域时才异步读取 Blob，并在上传前把较大的静态图片缩放、转换为 WebP 以减少等待时间，GIF 保持原格式。

## 资源接口

| 模块 | 查询 | 新增 | 修改 | 删除 |
|---|---|---|---|---|
| 任务计划 | `GET /tasks` | `POST /tasks` | `PUT /tasks/{id}` | `DELETE /tasks/{id}` |
| 项目 | `GET /projects` | `POST /projects` | `PUT /projects/{id}` | `DELETE /projects/{id}` |
| 知识领域 | `GET /domains` | `POST /domains` | `PUT /domains/{id}` | `DELETE /domains/{id}` |
| Markdown 文章 | `GET /markdown-documents` | `POST /markdown-documents` | `PUT /markdown-documents/{id}` | `DELETE /markdown-documents/{id}` |
| 代码片段 | `GET /snippets` | `POST /snippets` | `PUT /snippets/{id}` | `DELETE /snippets/{id}` |
| 流程图 | `GET /flowcharts` | `POST /flowcharts` | `PUT /flowcharts/{id}` | `DELETE /flowcharts/{id}` |
| 个人资料 | `GET /profile` | `POST /profile/avatar` | `PUT /profile` | `DELETE /profile/avatar` |

普通成员的所有查询都使用 JWT 中的用户 ID 过滤数据，不能指定 `ownerId`；只有服务端重新确认当前身份为管理员后，才会接受 `X-Workspace-Owner` 代管目标。

资料中的 `gender` 可为 `MALE`、`FEMALE`、`OTHER` 或 `null`，新注册用户默认为 `null`。未上传头像时，Web 客户端根据用户 ID 和昵称生成稳定的默认头像。`POST /profile/avatar` 使用名为 `file` 的 multipart 字段，最大 5 MB，只接受按文件字节识别出的 PNG、JPG、GIF 或 WebP；响应中的内部 `avatarUrl` 形如 `/user-avatars/{ownerId}`，通过 `GET /user-avatars/{ownerId}` 鉴权读取。`DELETE /profile/avatar` 删除私有 OSS 对象并恢复默认头像。旧数据中的外部 `avatarUrl` 仍兼容合法的 `http://` 或 `https://` 地址，服务端不会接受脚本协议或带账号凭据的 URL。

任务响应包含 `scheduledDate`（本地日历日期）、`dueAt`（UTC 时间点）、`priority`（`LOW` / `NORMAL` / `HIGH` / `URGENT`）、`completedAt` 和 `archived`。创建请求示例：

```json
{
  "title": "完成发布检查",
  "sortOrder": 0,
  "scheduledDate": "2026-08-30",
  "dueAt": "2026-08-30T10:00:00Z",
  "priority": "HIGH"
}
```

`scheduledDate` 和 `dueAt` 可为 `null`；未指定日期的任务属于收件箱。创建时省略 `priority` 会按 `NORMAL` 处理。更新请求还要提交 `done` 和 `archived`，完成状态变化时由服务端设置或清除 `completedAt`，客户端不能直接指定完成时间。现有 V9 之前的任务迁移后保留为收件箱，已完成任务使用最后更新时间补齐完成时间。

删除知识领域不会删除代码片段或流程图，已有内容自动回到“未分类”。代码片段和流程图删除后进入回收站，分别通过 `/snippets/trash`、`/flowcharts/trash` 查询，`POST /{资源}/{id}/restore` 恢复，`DELETE /{资源}/{id}/permanent` 彻底删除。流程图完整契约见 [FLOWCHART_API.md](FLOWCHART_API.md)。

Markdown 批量导入使用 `POST /markdown-documents/import`（JSON 或 Markdown/ZIP multipart），批量调整目录使用 `PATCH /markdown-documents/bulk-domain`，带图片的可移植 ZIP 导出使用 `POST /markdown-documents/export`。文章所有者还可以通过 `POST /markdown-documents/{id}/shares` 创建带有效期的公开只读链接，通过 `GET /markdown-documents/{id}/shares` 查看记录，并用 `DELETE /markdown-documents/{id}/shares/{shareId}` 撤销；访客使用无需登录的 `GET /public/markdown-shares/{token}` 阅读。请求格式、安全边界与图片分享说明参见 [MARKDOWN_API.md](MARKDOWN_API.md)。

已有代码片段单项公开链接继续有效：`POST/GET /snippets/{id}/shares` 创建或列表，`DELETE /snippets/{id}/shares/{shareId}` 撤销，`GET /public/knowledge-shares/{token}` 公开读取。流程图使用下文共享池与批量外链 API；不提供旧式单项分享端点。旧开发日志单项公开链接已删除。

统一知识列表可通过 `PATCH /knowledge-items/bulk-domain` 将文章、代码片段和流程图批量移动到同一目录。一次最多提交 100 条，三类资源都会按 JWT 所有者校验；目标目录不存在、任一内容不存在或文章/流程图版本冲突时，整批事务回滚。文章和流程图必须提交 `expectedVersion`，代码片段可省略：

```json
{
  "items": [
    { "type": "DOCUMENT", "id": "文章 ID", "expectedVersion": 3 },
    { "type": "SNIPPET", "id": "代码片段 ID" },
    { "type": "FLOWCHART", "id": "流程图 ID", "expectedVersion": 0 }
  ],
  "domainId": "目标目录 ID；传 null 表示未分类"
}
```

成功响应为 `204 No Content`。文章目录变化仍会递增版本并生成历史快照。

## 示例：新增知识领域

```json
{
  "name": "采购系统",
  "description": "供应商、询比价和采购订单相关知识",
  "sortOrder": 0
}
```

## 示例：新增代码片段

```http
POST /api/v1/snippets
Authorization: Bearer eyJ...
Content-Type: application/json
```

```json
{
  "title": "Dart 并发请求",
  "language": "Dart",
  "code": "final results = await Future.wait([]);",
  "favorite": true,
  "domainId": "领域 ID，可为空"
}
```

## 示例：新增流程图

`POST /flowcharts`，完整节点、连线、校验与历史契约见 [FLOWCHART_API.md](FLOWCHART_API.md)。

```json
{
  "title": "采购审批",
  "domainId": null,
  "favorite": false,
  "creationKey": "11111111-1111-4111-8111-111111111111",
  "diagram": { "schemaVersion": 1, "nodes": [], "edges": [] }
}
```

项目状态：`PLANNING`、`BUILDING`、`PAUSED`、`COMPLETED`。

## 错误格式

接口使用 RFC 9457 `ProblemDetail` 风格返回错误：

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "请求参数校验失败",
  "instance": "/api/v1/tasks",
  "fields": {
    "title": "不能为空"
  }
}
```

账户状态过滤器还会设置稳定错误码：`ACCOUNT_UNAVAILABLE` 表示账号已禁用或删除；`SESSION_REVOKED` 表示 JWT 已因安全操作失效；`PASSWORD_CHANGE_REQUIRED` 表示必须先修改管理员重置的临时密码；`TEMPORARY_PASSWORD_EXPIRED` 表示临时密码已过期。登录突发限流返回 `429`、`Retry-After` 响应头以及 `LOGIN_RATE_LIMITED`。

## 共享池与批量外链

`/api/v1/sharing/**` 需要有效登录并使用 `@WorkspaceOwner` 解析当前工作区。发布新分享和面向读者的读取要求作者启用；管理操作仅能作用于当前工作区自己的分享。启用管理员可通过已验证的 `X-Workspace-Owner` 管理禁用成员的分享：查询外链及条目、查看 `mine=true` 的共享池记录、撤下共享池、移除条目或撤销链接。该管理权限不恢复正文/图片的读者访问，也不允许为禁用成员新建分享；禁用成员自己的旧 JWT 仍返回 401。所有共享响应（包括错误）设置 `Cache-Control: no-store`，公开集合接口额外设置 `Referrer-Policy: no-referrer`。

内容类型为 `MARKDOWN`、`SNIPPET`、`FLOWCHART`，引用格式为 `{"type":"MARKDOWN","id":"资源 UUID"}`。一次发布或生成链接支持 1–100 项混合内容，禁止重复引用。引用不存在、属于其他账号或已在回收站时返回 404，整批失败，不留下部分发布或半成品链接。

| 方法与路径（均以 `/api/v1` 为前缀） | 请求 / 响应 |
| --- | --- |
| `POST /sharing/pool` | `{items: Reference[]}`；201 `{publishedCount,existingCount}`。重复发布已有有效条目只增加 `existingCount`。 |
| `GET /sharing/pool` | `page=0&size=20&q=&type=&mine=false`；`{items:PoolSummary[],total,page,size}`。按分享时间、ID 降序稳定分页，标题/正文/作者不区分大小写搜索。 |
| `GET /sharing/pool/{id}` | `SharedContent`，每次读取最新已保存正文。 |
| `GET /sharing/pool/{id}/images/{imageId}` | 当前单篇正文引用、且属于原作者的图片二进制。 |
| `POST /sharing/pool/revoke` | `{ids:string[]}`；204。支持 1–100 个共享条目 ID，原子校验所属工作区，可重复撤下。 |
| `POST /sharing/links` | `{title,items:Reference[],expiresAt}`；201 `{id,token,title,expiresAt,createdAt}`。标题 1–200 字；客户端默认 7 天、允许选择 1–365 天；服务端验证截止时间晚于当前且不超过 365 天。 |
| `GET /sharing/links` | `page=0&size=20&q=`；`{items:LinkSummary[],total,page,size}`，按创建时间、ID 降序；`q` 搜索集合标题。 |
| `GET /sharing/links/{id}` | `{link:LinkSummary,items:[{id,resourceType,resourceId,title,removedAt,available}]}`。移除或原文消失的条目保留在管理目录中。 |
| `DELETE /sharing/links/{id}` | 204，撤销整条链接，可重复执行。 |
| `POST /sharing/links/revoke` | `{ids:string[]}`；204，原子批量撤销 1–100 条本人链接。 |
| `DELETE /sharing/links/{id}/items/{itemId}` | 204，移除当前集合中的一项，可重复执行，不影响其他集合或共享池。 |
| `GET /public/share-bundles/{token}` | 无需登录；`{title,expiresAt,items:[{id,resourceType,title}]}`，只返回可用项摘要。 |
| `GET /public/share-bundles/{token}/items/{itemId}` | 无需登录；`SharedContent`，条目必须属于当前链接且仍可用。 |
| `GET /public/share-bundles/{token}/items/{itemId}/images/{imageId}` | 无需登录；验证令牌、条目、当前正文引用及图片作者后返回二进制。 |

`PoolSummary` 为 `{id,resourceType,title,excerpt,authorName,sharedAt,updatedAt,mine}`；`excerpt` 最多 240 字符。`mine=true` 是本人分享管理视图，可供管理员查看禁用成员的未撤下记录；面向成员的普通共享池始终排除禁用作者。`SharedContent` 为 `{id,resourceType,title,content,language,category,tags,updatedAt}`，其中 `id` 为共享条目 ID，非原文 ID。`language`/`category` 不适用时为 `null`，`tags` 不适用时为空数组。

`LinkSummary` 为 `{id,title,expiresAt,createdAt,revokedAt,active,itemCount}`。`itemCount` 是未移除且原文仍有效的条目数；`active` 还要求作者启用、未撤销、未到期且至少有一个有效条目。链接已移除最后一项、全部原文失效、作者禁用/删除、令牌无效、撤销或到期时，公开请求统一返回 404。页码须非负，每页 1–100 条，搜索文本最多 200 字；SQL 对 `%`、`_` 按普通字符搜索。

新链接使用 256 位随机令牌，数据库仅保存 SHA-256 摘要，明文只在创建响应显示一次，管理目录不返回令牌或摘要。共享池撤下与外链撤销互不影响。内容后续保存会同步到所有仍有效的分享。删除原文会递增分享代次，终止旧共享池条目、集合中的旧条目及旧单篇外链；恢复后需要主动重新发布，旧集合不会自动加入恢复后的内容。删除领域仅取消目录归属，不删除原文，也不撤销分享。

原 `markdown-documents/{id}/shares`、`snippets/{id}/shares`、`logs/{id}/shares` 和相应匿名单篇接口继续保留；旧链接也遵循作者启用和删除后不复活的规则。共享原文无法收回接收者已保存的副本。

图片权限通过 CommonMark 图片节点解析，支持可选标题、尖括号目标及引用式图片；普通链接、代码示例、原始 HTML 和非精确托管路径不授予图片权限，新旧分享使用同一解析器。图片仍须属于原作者且属于当前选中的单篇内容。

Flyway `V18` 新增共享表、索引、分享代次及代码片段/日志内部乐观锁版本。并发的旧保存、删除或恢复请求遇到版本变化返回 409，防止旧事务覆盖分享代次；客户端保留本地内容并重新加载。账号删除通过外键级联清理共享池、集合及集合条目。已有回收站内容的代次在迁移时递增，以免历史链接在恢复后意外重新生效。迁移不修改原文正文；在隔离 PostgreSQL 上验证后按常规部署流程执行。

## 健康检查

```http
GET /actuator/health
```

## 写入频率保护

保存文章和代码片段时，服务端按当前账号与资源类型做短窗口限流；默认 10 秒最多 12 次。超过限制返回 `429 Too Many Requests`、`code=SAVE_RATE_LIMITED` 和 `Retry-After`，读取接口不受影响。文章更新仍必须提交 `expectedVersion`，客户端应同时合并进行中的相同保存请求，避免重复写入和重复历史。


## 管理员模拟登录与业务编辑

管理员在人员管理中选择“模拟登录 / 管理数据”，进入与普通成员共用的工作台、项目、知识库、工具和资料页面。顶部标明当前成员，返回人员管理会退出模拟登录。刷新和页面导航通过 URL 的 `workspace` 参数恢复目标，但 URL 本身不授予权限。

- `GET /api/v1/admin/accounts/{userId}/workspace/account`：返回目标账户基本信息（与旧工作区快照的 `account` 字段相同），响应 `Cache-Control: no-store`。未登录 401，非管理员 403，不存在 404。
- 所有业务 API（`tasks`、`projects`、`domains`、`knowledge-items`、`snippets`、`logs`、`profile`、`markdown-documents`、`markdown-images`）接受可选请求头 `X-Workspace-Owner: <目标用户 UUID>`。
- 请求仍使用管理员本人的 Bearer Token。后端先验证原会话的启用状态、会话版本和强制改密要求，再重新读取数据库确认 ADMIN 权限和目标账户存在；禁用成员的业务数据仍可维护。
- 不带该请求头时，沿用当前登录账号的数据。普通成员携带该头返回 403；标识格式错误或多个目标返回 400；目标或目标名下资源不存在返回 404。
- 新增、编辑、删除、收藏、置顶、目录批量整理、历史恢复、回收站、导入导出、分享与图片均沿用业务 API 的参数、校验及状态码。管理员写入仍保留文章 `expectedVersion` 并发检查，冲突返回 409，不允许强行覆盖。
- `/auth/**`、`/admin/**` 不使用该请求头，身份与账号管理操作始终属于真实登录管理员；模拟登录不签发成员 Token，也不改变成员密码、角色或登录状态。
- 原 `/admin/accounts/{userId}/workspace` 快照及专用文章/图片读取接口保持兼容。
- 客户端在切换工作区时取消旧请求、拒绝迟到响应，清除业务缓存及 KeepAlive 页面；本机草稿和通知按“操作者 + 目标成员”隔离。

示例：使用管理员 JWT 向成员新增任务：

```http
POST /api/v1/tasks
Authorization: Bearer <管理员 Token>
X-Workspace-Owner: <目标成员 UUID>
Content-Type: application/json

{"title":"整理成员知识库","sortOrder":0,"scheduledDate":null,"dueAt":null,"priority":"NORMAL"}
```

流程图共享详情新增 `diagram` JSON 对象，`content` 是节点/连线标签提取文本；其他共享类型的 `diagram` 为 null。
