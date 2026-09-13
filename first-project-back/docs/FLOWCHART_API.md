# 流程图 API

所有路径相对于 `/api/v1`，需要 JWT；管理员通过经验证的 `X-Workspace-Owner` 选择成员工作区。其他所有者的文档、历史和写入请求均返回 404。

| 方法与路径 | 行为 |
|---|---|
| `GET /flowcharts?q=` | 按标题、节点及连线标签搜索，仅返回摘要数组，排除回收站 |
| `POST /flowcharts` | 创建，201；同一所有者的 `creationKey` 重试返回首次文档，不覆盖内容；原文档在回收站时 409 |
| `GET /flowcharts/{id}` | 摘要字段及结构化 `diagram` |
| `PUT /flowcharts/{id}` | 提交 `expectedVersion`；冲突 409，成功返回最新版本 |
| `DELETE /flowcharts/{id}` | 移入回收站，204，并永久作废已有分享代次 |
| `GET /flowcharts/trash` | 回收站摘要数组 |
| `POST /flowcharts/{id}/restore` | 从回收站恢复，不复活旧分享 |
| `DELETE /flowcharts/{id}/permanent` | 仅允许回收站内容；彻底删除文档和历史，204 |
| `GET /flowcharts/{id}/revisions?page=0` | 历史摘要数组，每页 20 条，版本倒序 |
| `GET /flowcharts/{id}/revisions/{revisionId}` | 单个历史快照及结构化 `diagram` |
| `POST /flowcharts/{id}/revisions/{revisionId}/restore` | 请求 `{ "expectedVersion": 3 }`，恢复为新版本 |

创建草稿为 `{title,domainId,favorite,diagram,creationKey}`；`creationKey` 必须是 UUID。更新草稿为 `{title,domainId,favorite,diagram,expectedVersion,saveMode}`，`expectedVersion` 为非负整数，`saveMode` 为 `AUTO` 或 `MANUAL`（省略时按手动保存）。`title` 去掉两端空白后必须为 1–200 字符，`domainId` 可为 null。服务端通过行锁、乐观版本及所有者创建键唯一约束避免并发覆盖和重复创建。频繁保存使用已有账号级限流，429 响应提供重试信息；客户端应保留草稿。

摘要包含 `id,title,domainId,favorite,createdAt,updatedAt,version,deletedAt,nodeCount,edgeCount,excerpt`；数据库投影不加载 diagram 正文。`excerpt` 是提取标签的前 240 字符。详情仅增加 `diagram`。历史摘要为 `id,documentVersion,title,action,createdAt`，详情增加 `domainId,favorite,diagram`。

自动历史检查点至多每五分钟一个；创建、变更后的手动保存、历史恢复及目录移动生成检查点。连续相同草稿不生成重复快照；手动保存可把此前自动保存的最新状态确认为检查点。最多保留最新 100 条。视口、选择和编辑器控制状态不属于云端草稿。

## 可移植图模型

`diagram` 是 JSON 对象，形如 `{ "schemaVersion": 1, "nodes": [], "edges": [] }`。所有模型字段均必填，任意层级的未知字段均返回 400。

- 节点：`{id,kind,label,x,y,width,height,zIndex,style}`。kind 为 `terminal|rectangle|rounded|diamond|io|database|text`。style 为 `{fill,stroke,strokeWidth,dash,textColor,fontSize,textAlign}`；textAlign 为 `left|center|right`。
- 连线：`{id,kind,source,target,label,vertices,zIndex,sourceArrow,targetArrow,style}`。kind 为 `straight|orthogonal`；端点为 `{nodeId,port}`，port 为 `top|right|bottom|left`；vertices 为 `{x,y}[]`。style 为 `{stroke,strokeWidth,dash,textColor,fontSize}`。箭头和 dash 为布尔值。
- ID 是 1–80 位 ASCII 字母、数字、下划线或连字符，节点与连线之间也不能重复；端点必须引用已有节点。允许空图、循环、自环。
- 坐标范围 ±1,000,000，尺寸 10–10,000，字号 8–96，线宽 0–12，整数层级 ±10,000；只允许有限数值。标签至多 10,000 字符，每线至多 100 个折点。
- 颜色仅允许 `#RRGGBB` 或 `#RRGGBBAA`；fill 另支持 `transparent`。禁止图片、HTML 引擎节点、外部资源和任意引擎配置。标签按普通文字处理，类似 HTML 的文字仍为原样文本。
- 单图序列化 UTF-8 至多 5 MiB、1,000 节点和 2,000 连线。

## 知识管理及分享

`PATCH /knowledge-items/bulk-domain` 的流程图类型为 `FLOWCHART`，必须提交每个流程图的 `expectedVersion`；与文章、代码片段混合移动时任一错误整批回滚。删除目录后流程图回到未分类。

共享池与外部集合使用已有 `/sharing/pool`、`/sharing/links`，资源类型为 `FLOWCHART`。详情中的 `diagram` 为结构化图，`content` 为搜索文字；其他资源的 `diagram` 为 null。分享始终读取最新已保存状态；进入回收站即永久失效，恢复后需要重新分享。流程图不使用旧的单资源分享 API；已有代码片段公开链接仍有效。

V19 用流程图替换开发日志；旧 `/logs` 路径返回 404，旧日志公开链接、共享池记录及集合日志项被清除。仅含日志的集合保留记录但被撤销；混合集合的非日志项、链接令牌和 ID 保持不变。备份与升级方式见 [POSTGRESQL_SETUP.md](POSTGRESQL_SETUP.md)。
