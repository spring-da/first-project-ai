# 共享池与批量外链分享

本次按用户已授权的功能修改推进，保留工作区已有改动。沿用 Vue 3、Pinia、Spring Boot、PostgreSQL 与现有主题、弹窗、权限模式。

## 行为

- 知识库单篇或混合勾选文章、代码片段、开发日志后，在同一个分享弹窗选择内部共享池或外部链接，单次最多 100 项。
- 共享池仅登录且启用的成员可查看，支持标题/正文/作者搜索、类型筛选、分页和“我的分享”。重复发布不产生重复条目。分享者可单篇或批量撤下，重新发布必须主动操作。
- 外部分享生成一个包含多项内容的只读链接，默认 7 天，允许 1–365 天。令牌只展示一次、数据库仅存摘要。访问端先展示目录，按需加载单篇正文及图片。
- 在共享池的“外链管理”页管理新链接，查看包含的条目，可整条/批量撤销或移除其中一项。撤销和移除前说明准确影响并确认。旧单篇外链仍可在对应笔记的分享弹窗中查看并撤销。
- 共享池撤下和外链撤销互不影响。展示最新已保存内容，弹窗明确后续保存也会同步。删除原文终止相应分享；恢复原文不自动公开。禁用/删除作者后停止访问。
- API 每次验证权限、撤销、过期、原文存活与图片引用，响应 no-store。已被接收者保存的内容无法收回。
- 数据批量操作事务化，任一非法/非本人条目使整个请求失败；所有者来自 WorkspaceOwner，公开访问不使用工作区 JWT。

## 接口契约（/api/v1 前缀）

类型为 MARKDOWN、SNIPPET、DEV_LOG。输入引用为 `{type,id}`。

- POST `/sharing/pool`，`{items: Reference[]}` → `{publishedCount,existingCount}`。
- GET `/sharing/pool?page=0&size=20&q=&type=&mine=false` → `{items:[{id,resourceType,title,excerpt,authorName,sharedAt,updatedAt,mine}],total,page,size}`。
- GET `/sharing/pool/{id}` → SharedContent；GET `/{id}/images/{imageId}` → 二进制。
- POST `/sharing/pool/revoke`，`{ids:string[]}` → 204，幂等。
- POST `/sharing/links`，`{title,items:Reference[],expiresAt}` → `{id,token,title,expiresAt,createdAt}`。
- GET `/sharing/links?page=0&size=20&q=` → `{items:LinkSummary[],total,page,size}`。
- GET `/sharing/links/{id}` → `{link:LinkSummary,items:[{id,resourceType,resourceId,title,removedAt,available}]}`。
- DELETE `/sharing/links/{id}` → 204；POST `/sharing/links/revoke`，`{ids}` → 204。
- DELETE `/sharing/links/{id}/items/{itemId}` → 204，幂等；最后一项移除后公开访问失败。
- GET `/public/share-bundles/{token}` → `{title,expiresAt,items:[{id,resourceType,title}]}`。
- GET `/public/share-bundles/{token}/items/{itemId}` → SharedContent。
- GET `/public/share-bundles/{token}/items/{itemId}/images/{imageId}` → 二进制。

SharedContent: `{id,resourceType,title,content,language:string|null,category:string|null,tags:string[],updatedAt}`。
LinkSummary: `{id,title,expiresAt,createdAt,revokedAt:string|null,active,itemCount}`。

## 验证

后端：三类型混合批量创建、原子失败、搜索分页、重复发布、权限隔离、无登录访问、撤销、到期、部分移除、删后恢复、账号禁用、图片范围。运行完整 Maven test/package 和独立 PostgreSQL/Flyway 验证。
前端：选择/有效期等纯逻辑与 API 行为测试、类型检查及生产构建；隔离 UI 验证单篇/批量两渠道、管理撤销、只读集合、错误和空状态、深浅主题及 320/390/1024/1440px。
