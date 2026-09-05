# DevNest Web 开发指引

## 适用范围

本文件适用于当前前端项目根目录及其全部子目录。项目是 DevNest 的浏览器端单页应用，与同级目录 `../first-project-back` 的 REST API 配套使用。修改接口契约时必须同时检查后端实现和文档。

## 技术栈与运行方式

- Vue 3.5、Composition API、`<script setup lang="ts">`
- TypeScript 6，启用未使用变量/参数和 fallthrough 检查
- Pinia 3 管理跨页面状态
- Vue Router 4，使用 History 模式和懒加载路由
- Vite 8；图标统一使用 `lucide-vue-next`
- 推荐 Node.js 22.12+；Docker 构建使用 Node 24

常用命令：

```powershell
npm ci
npm run dev
npm test
npm run build
npm run preview
```

本地开发默认把 `/api` 代理到 `http://127.0.0.1:8080`。生产构建由 Nginx 提供静态文件，并将 `/api/` 转发到后端容器。

## 架构地图

- `src/main.ts`：创建 Vue、Pinia、Router，初始化主题，注册鉴权路由守卫和 401 会话失效处理。
- `src/App.vue`：全局路由出口、通知宿主和确认框宿主；把 Store 错误转为全局通知。
- `src/router/index.ts`：路由定义。工作台、项目、知识库、人员管理和个人资料位于 `AppLayout` 下；管理员路由使用 `adminOnly`，主要业务页面使用 `keepAlive`。
- `src/layouts/AppLayout.vue`：侧栏、顶栏、全局/页面搜索、快捷键和工作区首次加载。
- `src/views/`：路由级页面。页面负责组合组件，不要在这里重复实现通用基础设施。
- `src/components/`：可复用 UI。Markdown 编辑、通知、确认框和搜索范围选择器已经有专用组件。
- `src/stores/`：跨页面状态和业务操作。
  - `auth.ts`：JWT 会话及登录/注册。
  - `admin.ts`：管理员人员目录、注册邀请、账号启禁用、密码重置和删除。
  - `workspace.ts`：任务、项目、目录、文章、片段、日志和资料的远端缓存与 CRUD。
  - `notifications.ts`：Toast 和按账号隔离的消息历史。
  - `confirmation.ts`：异步自定义确认框。
  - `search.ts`、`theme.ts`：搜索范围和主题偏好。
- `src/services/`：HTTP 边界。`api.ts` 统一处理 API Base URL、JWT、超时、401、JSON 错误和下载；`markdownImages.ts` 负责鉴权图片上传/读取。
- `src/utils/`：可独立测试的纯逻辑，包括搜索、Markdown 渲染、草稿合并、快捷键、图片校验和客户端 ID。
- `src/types/index.ts`：前后端共享概念在前端的类型契约。
- `src/style.css`：主题变量、全局布局和跨页面通用样式；组件专属样式优先放在组件的 scoped style 中。
- `tests/*.test.ts`：Node 原生测试运行器执行的逻辑测试。
- `tests/ui/`：挂载生产组件、使用内存数据和模拟 API 的隔离浏览器回归入口；不得连接真实账号或笔记。

## 数据与控制流

1. `AppLayout` 挂载时调用 `workspace.loadAll()` 加载账号工作区。
2. View 读取 Pinia 中的响应式状态，并调用 Store action 完成业务操作。
3. Store action 通过 `apiRequest` / `apiDownload` 请求后端，再用服务端响应更新本地状态。
4. API 或 Store 错误由 `App.vue` 转为通知；视图不要再弹一次相同错误。
5. 后端返回 401 时 `api.ts` 清除会话并发出 `devnest:unauthorized`，入口统一清空账号状态并跳转登录页。

接口字段发生变化时，至少检查：`src/types/index.ts`、对应 Store action、相关 View/Component、测试夹具、后端 DTO 和 `docs/API.md`。

## 编码约定

- 延续 Composition API 和 `<script setup>`；不要引入 Options API 或另一套状态管理库。
- 远端共享数据放在 Store；临时弹窗、输入草稿和视图模式可以留在组件内。
- 所有 API 调用通过 `src/services/api.ts`。不要在页面中手写 Base URL、Authorization 或重复的 `fetch` 错误处理。
- JSON 请求使用 `jsonBody`；上传 `FormData` 时不要手动设置 `Content-Type`，浏览器必须生成 multipart boundary。
- 用户可见的警告、成功和失败使用通知 Store；危险或不可逆操作使用确认 Store。不要调用原生 `alert`、`confirm` 或 `prompt`。
- 优先复用 `AppModal`、`EmptyState`、`WorkspaceUtilities`、`PageHeader` 等已有组件。
- 图标从 `lucide-vue-next` 引入；不要加入字符图标、Emoji 或另一套图标库。
- 保留键盘操作、焦点样式、语义标签、`aria-label`/`aria-expanded` 等可访问性信息。
- 监听 `window`/`document`、计时器或网络请求时，必须在卸载/停用时清理。路由页面使用 KeepAlive，必要时同时处理 `onActivated` 和 `onDeactivated`。
- 不要通过删减功能规避窄屏问题。检查展开/收起侧栏、深浅主题、320/390px 手机、平板和宽屏布局，避免页面横向溢出。
- 保持现有 CSS 变量体系。共享 token/基础布局放 `src/style.css`；单个页面或组件的规则放 scoped style。

## 管理员与人员管理

- `ADMIN` 菜单和 `/admin/accounts` 仅对 `auth.isAdmin` 显示/放行，路由继续使用 `meta.adminOnly`。这只是客户端体验保护，真正的角色与账号状态校验必须由后端完成。
- `AdminAccountsView.vue` 同时呈现已注册账号与待注册邮箱，并保留统计、搜索及“全部/已注册/待注册/已禁用”筛选。管理操作统一通过 `stores/admin.ts` 调用 `/admin/**`，页面不能直接请求接口。
- 管理员添加邮箱或轮换邀请时，接口会返回只显示一次的邀请 Token；页面使用 URL fragment 生成注册链接，避免 Token 进入服务器访问日志，并通过一次性敏感信息弹窗提供复制。人员目录不得缓存或重新展示明文 Token。
- 管理员重置密码后，接口会返回只显示一次、默认 30 分钟过期的随机临时密码。页面只在当前弹窗内保存明文，关闭即清除；不得写入 Store、浏览器存储、通知正文或日志。禁用、重置、撤销资格和永久删除继续使用现有通知/确认 Store。
- 当前管理员以及其他管理员不得显示可执行的禁用、重置或删除操作。前端限制不能替代服务端保护，也不要允许客户端编辑或提交角色。
- 会话中的 `mustChangePassword` 为真时，路由守卫必须强制跳转 `/change-password`；改密前不得进入其他业务页面。后端返回 `PASSWORD_CHANGE_REQUIRED` 时也要更新本地会话并进入强制改密流程。
- 注册请求只提交 URL fragment 中的一次性 `invitationToken`、显示名称和密码，不再提交或让用户编辑受邀邮箱。没有邀请 Token 时只能展示获取邀请的提示，不得退回到按邮箱直接注册。
- 人员管理页在展开侧栏时必须占满剩余工作区，不得重新添加固定的页面 `max-width` 造成右侧大面积留白。内部统计卡片、邀请表单、筛选工具和账户行应随宽屏调整列宽，并继续保证 1100px 以下及 320/390px 手机端无横向溢出。
- 管理员可模拟登录成员工作区并修改全部业务数据。前端复用正常成员页面和按当前工作区隔离的 Store；管理员 JWT 始终保留原身份，通过 `X-Workspace-Owner` 指定目标账户。后端仅在 `@WorkspaceOwner` 业务参数解析器中允许启用的 ADMIN 选择目标，并逐次校验数据库角色；普通成员仍只能访问自己的资源。草稿、通知、图片缓存和异步响应必须按管理员及目标成员隔离，退出时清空工作区并销毁页面缓存。身份认证、改密及人员管理仍以原 JWT 身份执行。
- 修改管理员功能时至少同步检查 `src/types/index.ts`、`stores/auth.ts`、`stores/admin.ts`、`main.ts` 路由守卫、`AppLayout.vue`、`AdminAccountsView.vue`、`ChangePasswordView.vue`、隔离 UI 夹具和后端 `docs/API.md`。

## Markdown 特有约束

- 本机草稿按账号、文档和编辑会话隔离。存储失败不能删除其他草稿；保存响应必须保留请求进行期间的新输入。
- 云端保存和历史恢复使用 `expectedVersion` 处理并发冲突，不要移除版本检查。
- Markdown 渲染器必须继续转义原始 HTML，并限制链接/图片 URL 协议。不要直接把未经处理的正文交给 `v-html`。
- Markdown 图片始终通过后端稳定地址和带 JWT 的二进制读取生成 Blob URL；即使 Bucket 配置为公共读，正文、DOM URL 和前端环境变量中也不能出现 JWT、OSS AccessKey、对象键或签名 URL。
- 图片上传期间允许继续输入，但保存/离开必须阻止临时上传占位进入云端文档或本机草稿。
- 导入支持多选 `.md`/`.markdown`/`.mdown` 和 ZIP。导入到真实当前目录；在“全部知识”或“未分类”下导入时归入未分类。ZIP 图片由后端解析上传，前端不得自行解压或直连 OSS。批量移动目录必须提交列表响应中的每篇 `version`。
- `Ctrl/Cmd+S` 仅在活动编辑器内拦截浏览器默认行为；弹窗、通知抽屉或非编辑页面不能误触发保存。

## 测试与验证

- 修改纯逻辑时，在现有 `tests/*.test.ts` 中补充最接近的测试；不要为了覆盖配置常量创建冗余测试。
- 修改页面布局或交互时，先运行自动测试和生产构建，再使用 `tests/ui/notifications-editor.html` 做隔离浏览器检查。操作步骤见 `tests/ui/README.md`。
- 管理员页面使用隔离入口的 `?admin=1` 场景，强制改密页面使用 `?force-password=1`；不得用真实管理员账号或生产数据进行 UI 回归。
- 最低验证：

```powershell
npm test
npm run build
```

- `npm run build` 同时执行 `vue-tsc -b`，类型检查失败视为未完成。
- 不要手工编辑或提交 `node_modules/`、`dist/`、`.ui-test-dist/` 或 TypeScript 构建缓存。

## 配置、安全与部署

- 前端唯一公开配置是 `VITE_API_BASE_URL`。所有 `VITE_*` 值都会进入浏览器产物，严禁放数据库密码、JWT 密钥或 OSS 密钥。
- 登录会话和本机草稿存放在浏览器存储中；新增存储必须按账号隔离，并能在存储不可用时安全降级。
- 导出、图片和其他二进制响应也必须经过鉴权 API 工具，不要把 Bearer Token 放进查询参数。
- 调整上传大小或接口超时时，要同步检查 `nginx.conf`、后端 multipart 配置和客户端超时。
- 部署修改后重新生成 `dist/`；若接口或 Nginx 行为变化，还要同步部署后端和 Nginx，不能只替换前端静态文件。
