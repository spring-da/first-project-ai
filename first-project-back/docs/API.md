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

JWT 包含用户当前的 `authVersion`。禁用、重置密码和修改密码都会递增版本，因此操作前签发的全部 JWT 会立即失效。重置密码还会设置 `mustChangePassword=true`。永久删除会通过现有 MySQL 外键级联删除该账号的工作区数据，并撤销其注册资格；若以后需要重新注册，管理员必须再次添加该邮箱。管理员账号不能被禁用、重置或删除。

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
| 开发日志 | `GET /logs` | `POST /logs` | `PUT /logs/{id}` | `DELETE /logs/{id}` |
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

删除知识领域不会删除片段或日志，已有内容会自动回到“未分类”。删除代码片段或开发日志（`DELETE /snippets/{id}`、`DELETE /logs/{id}`）现在改为移入回收站，普通列表不再返回；恢复用 `POST /snippets/{id}/restore`、`POST /logs/{id}/restore`，彻底删除（只能作用于已在回收站的内容）用 `DELETE /snippets/{id}/permanent`、`DELETE /logs/{id}/permanent`，回收站列表为 `GET /snippets/trash`、`GET /logs/trash`（按删除时间倒序，返回 `id/title/语言或类型/excerpt/domainId/deletedAt`）。回收站内容不会自动清空；恢复时若原目录已被删除，内容会自动回到“未分类”。

Markdown 批量导入使用 `POST /markdown-documents/import`（JSON 或 Markdown/ZIP multipart），批量调整目录使用 `PATCH /markdown-documents/bulk-domain`，带图片的可移植 ZIP 导出使用 `POST /markdown-documents/export`。文章所有者还可以通过 `POST /markdown-documents/{id}/shares` 创建带有效期的公开只读链接，通过 `GET /markdown-documents/{id}/shares` 查看记录，并用 `DELETE /markdown-documents/{id}/shares/{shareId}` 撤销；访客使用无需登录的 `GET /public/markdown-shares/{token}` 阅读。请求格式、安全边界与图片分享说明参见 [MARKDOWN_API.md](MARKDOWN_API.md)。

代码片段和开发日志使用相同的限时分享规则。所有者分别通过 `POST /snippets/{id}/shares` 或 `POST /logs/{id}/shares` 创建链接，通过对应的 `GET` 地址查看历史，并使用 `DELETE /snippets/{id}/shares/{shareId}` 或 `DELETE /logs/{id}/shares/{shareId}` 撤销。访客通过无需登录的 `GET /public/knowledge-shares/{token}` 读取只读内容。服务端只保存令牌的 SHA-256 摘要，明文令牌只在创建响应中返回一次；资源进入回收站、链接过期或被撤销后会立即停止公开访问。

统一知识列表可通过 `PATCH /knowledge-items/bulk-domain` 将文章、代码片段和开发日志批量移动到同一目录。一次最多提交 100 条，三类资源都会按 JWT 所有者校验；目标目录不存在、任一内容不存在或文章版本冲突时，整批事务回滚。文章必须提交 `expectedVersion`，代码片段和日志可省略：

```json
{
  "items": [
    { "type": "DOCUMENT", "id": "文章 ID", "expectedVersion": 3 },
    { "type": "SNIPPET", "id": "代码片段 ID" },
    { "type": "LOG", "id": "开发日志 ID" }
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

## 示例：新增开发日志

```json
{
  "title": "完成后端 Repository 设计",
  "content": "使用 Spring Data JPA 隔离 MySQL 数据访问。",
  "category": "DECISION",
  "tags": ["Java", "Spring Boot", "MySQL"],
  "pinned": false,
  "domainId": "领域 ID，可为空"
}
```

日志类型：`PROBLEM`、`DECISION`、`LEARNING`、`IDEA`。

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
