# DevNest Backend 开发指引

## 适用范围

本文件适用于当前后端项目根目录及其全部子目录。项目为 DevNest Web/Android 提供 REST API；同级目录 `../first-project-web` 是浏览器客户端。任何接口契约修改都要检查前端类型、Store 和调用点。

## 技术栈与运行方式

- Java 26
- Spring Boot 4.1 / Spring Framework 7
- Spring MVC、Bean Validation
- Spring Security 7、OAuth2 Resource Server、HS256 JWT
- Spring Data JPA / Hibernate，PostgreSQL 17+ / pgvector 0.8.2
- Flyway 数据库迁移
- 阿里云 OSS Java SDK 3.18.4，用于 Markdown、意见附件和用户头像图片
- Maven；测试使用 JUnit、AssertJ、Mockito、Spring Security Test、MockMvc、H2 PostgreSQL 模式和独立 PostgreSQL

常用命令：

```powershell
mvn -B -ntp test
mvn -B -ntp package
mvn spring-boot:run
```

运行应用需要数据库、JWT 和 CORS 环境变量；测试在 H2/Mock 中运行，不应连接生产数据库或 OSS。

## 架构地图

- `src/main/java/com/springda/devnest/DevNestBackendApplication.java`：Spring Boot 入口和配置属性启用点。
- `config/`：JWT、Spring Security、CORS 和应用配置属性。
- `auth/`、`user/`：邀请制注册、登录、改密、用户实体和 JWT 签发。
- `admin/`：管理员人员目录、注册邀请、账号启禁用、密码重置和永久删除。
- `task/`、`project/`、`knowledge/`、`snippet/`、`log/`、`profile/`：主要业务模块。
- `markdown/`：Markdown 文章、软删除/回收站、历史版本、并发版本检查、批量导入和 ZIP 导出。
- `image/`：Markdown 图片元数据、文件签名检查、OSS 存取和按账号鉴权读取。
- `common/`：基础实体、业务异常和统一 `ProblemDetail` 错误响应。
- `src/main/resources/application.yml`：环境变量映射、JPA/Flyway、multipart、Actuator 和连接池配置。
- `src/main/resources/db/postgresql/`：PostgreSQL V17 基线，后续从 V18 递增。
- `src/test/java/`：服务测试与 Spring/MockMvc 集成测试。
- `docs/`：API、Markdown 导入导出/恢复和 OSS 部署说明。
- `compose.yml`：PostgreSQL/pgvector、后端、前端。

## 请求与数据流

典型请求链路：

```text
SecurityFilterChain → JWT 解析 → Controller → Service → Repository → Entity
                                           ↘ DTO Response
```

- 只有 `POST /api/v1/auth/login`、`POST /api/v1/auth/register`、`GET /api/v1/auth/display-name-availability`、`/actuator/health` 和 OPTIONS 可匿名访问，其他请求默认都需要 JWT。
- 业务 Controller 使用 `@WorkspaceOwner String ownerId`；认证与人员管理 Controller 使用 `@AuthenticationPrincipal Jwt` 保留原始操作者身份。
- Controller 只处理 HTTP 状态、路径、校验和 DTO；事务与业务规则放在 Service。
- Service 使用 `@Transactional`；只读查询标注 `@Transactional(readOnly = true)`。
- Repository 访问用户资源时必须把 `ownerId` 放入查询条件。
- Entity 不直接作为 API 响应；沿用各业务包的 `*Dtos` records 完成输入校验和输出映射。
- `GlobalExceptionHandler` 把业务异常、校验错误、并发冲突和上传问题统一转换为 RFC Problem Details。

## 多租户与安全边界

- 普通成员的 ownerId 来自 JWT subject；管理员业务请求可携带 `X-Workspace-Owner`，但必须由 `WorkspaceOwnerResolver` 校验当前管理员与目标账户，禁止在业务 Service 中取消所有者约束。
- 按 ID 读取、修改、删除时必须使用 `findByIdAndOwnerId(...)` 或等价的所有者约束，不能先按 ID 取出再在 Controller 里过滤。
- 对其他账号的资源返回 404，避免泄露资源是否存在；新增测试要覆盖跨账号访问。
- 密码使用现有 BCrypt(12)；JWT 密钥必须是至少 32 字节随机内容的 Base64，禁止写入源码、日志或测试快照。
- CORS 来源来自配置，生产环境只允许真实站点。不要使用通配来源配合凭证。
- 不要在响应、异常信息或日志中暴露数据库连接串、JWT、OSS AccessKey、对象键、签名 URL 或 SDK 原始异常。

## 持久化与迁移规则

- `BaseEntity` 统一使用 36 字符 UUID 和 UTC `Instant` 创建/更新时间；新实体应保持相同策略。
- JPA 配置为 `ddl-auto=validate`。生产表结构只能通过 Flyway 修改。
- 已发布的迁移文件不可编辑、重命名或重排。下一次结构变化创建新的顺序迁移，并同步 Entity、索引、测试和文档。
- PostgreSQL 迁移使用 TEXT、VARCHAR UUID 和 TIMESTAMPTZ(6)，保持外键、账号隔离、版本及唯一约束。H2 通过不能替代真实 PostgreSQL/Flyway 验证。
- 不得为了让开发环境启动而改成生产 `create`/`update`/`create-drop`。`create-drop` 只允许测试属性使用。
- 删除、级联和唯一约束必须明确评估已有数据、账号隔离及回滚/备份影响；不要在未获授权时执行生产迁移或破坏性数据命令。

## 业务实现约定

- 沿用每个业务包中的 `Controller → Service → Repository → Entity/DTO` 组织方式，不建立跨业务的“大服务”。
- 请求使用 Bean Validation；仍需在 Service 中执行依赖数据库状态的业务校验和字符串规范化。
- 资源不存在使用 `NotFoundException`，非法输入使用 `BadRequestException`，状态/版本冲突使用 `ConflictException`；不要从 Controller 返回临时错误 Map。
- 返回 201 的创建接口使用 `@ResponseStatus(HttpStatus.CREATED)`，成功删除返回 204，保持现有 API 语义。
- 业务写入必须在事务中完成。涉及数据库与 OSS 两种资源时，要考虑数据库失败后的对象清理以及重复请求。

### 账号、注册与管理员

- 系统采用一次性 Token 邀请注册。管理员添加邮箱时生成 256 位随机 Token，数据库只保存 SHA-256 摘要；明文 Token 只在创建或轮换响应中显示一次，默认 48 小时过期，注册成功后立即销毁。公开注册只接收邀请 Token，不接收邮箱，所有无效、过期、已使用或邮箱已注册场景使用同一错误信息，避免枚举邮箱状态。
- 默认 `BOOTSTRAP_ADMIN_REQUIRED=false`，允许空库启动后导入已有账号；导入时保持 `BOOTSTRAP_ADMIN_EMAIL` 和 `BOOTSTRAP_ADMIN_PASSWORD` 为空。不导入数据的全新安装可同时配置邮箱及 16–72 位密码创建唯一初始管理员；只要库中已有账号便永久忽略自举配置，首次登录改密后应从环境中删除两个变量。不得通过公开注册参数、普通资料编辑或客户端提交的角色字段授予管理员权限。
- `/api/v1/admin/**` 仅允许启用状态的 `ADMIN`。Spring Security 负责路由拦截，Admin Service 仍需重新校验当前账号的存在、启用状态和角色，不能只信任旧 JWT 或前端隐藏菜单。
- 人员目录同时展示已注册账号和待注册邮箱。管理员可以添加/撤销注册资格、启用/禁用普通账号、重置密码和永久删除普通账号；管理员账号不得被禁用、重置或删除。
- 每个 JWT 必须携带用户当前 `authVersion`。禁用、管理员重置密码和用户改密都要递增版本，使此前签发的全部 JWT 立即失效；账号状态、角色、临时密码过期时间和强制改密状态仍在每个受保护请求中从数据库读取。
- 管理员重置密码时生成 20 位高强度随机临时密码，使用 BCrypt(12) 保存并设置 `forcePasswordChange=true`，默认 30 分钟过期。明文仅通过 `Cache-Control: no-store` 的重置响应显示一次，不得持久化或写入日志、异常和人员目录响应。
- 登录默认每个“邮箱 + 来源地址”每分钟最多 10 次；已注册账号连续失败 5 次后锁定 15 分钟。未知邮箱、密码错误、账号禁用、锁定和临时密码过期应保持相同的认证失败响应，不得泄露账号状态。
- 强制改密账号只允许访问 `GET /api/v1/auth/me` 和 `PUT /api/v1/auth/password`；完成改密后清除强制标记并签发反映新状态的会话，其他业务接口统一返回 `PASSWORD_CHANGE_REQUIRED`。
- 永久删除账号会依赖现有外键级联清理该账号的工作区数据，并撤销其注册资格。接口必须保持管理员保护和事务边界；不得提供绕过确认的批量删除或允许删除管理员。
- 修改本功能时同步检查 `AdminController`、`AdminService`、`AuthService`、`SecurityConfig`、用户实体/DTO、PostgreSQL 迁移、`docs/API.md` 和前端人员管理页面。
- 管理员可模拟登录成员工作区并修改全部业务数据。前端复用正常成员页面和按当前工作区隔离的 Store；管理员 JWT 始终保留原身份，通过 `X-Workspace-Owner` 指定目标账户。后端仅在 `@WorkspaceOwner` 业务参数解析器中允许启用的 ADMIN 选择目标，并逐次校验数据库角色；普通成员仍只能访问自己的资源。草稿、通知、图片缓存和异步响应必须按管理员及目标成员隔离，退出时清空工作区并销毁页面缓存。身份认证、改密及人员管理仍以原 JWT 身份执行。

### Markdown

- Markdown 文章使用 `@Version`，更新和历史恢复接收 `expectedVersion`。不要移除显式版本检查，也不要在冲突时覆盖云端内容。
- 创建、内容变化、回收站恢复和历史恢复会生成快照；相同内容保存不应制造重复快照。
- 普通查询和导出排除回收站；永久删除只能作用于已在回收站的文章，并删除对应历史。
- 历史列表按页读取，当前每页 20 条。新增分页行为时保持稳定排序并更新客户端。
- 导入/导出文件名、数量和正文大小继续遵守 `docs/MARKDOWN_API.md` 的限制。
- 文件导入同时支持批量 Markdown 与 ZIP。ZIP 中只把正文实际引用的相对图片上传 OSS，并把引用改写为稳定图片 API；上传对象须在外层数据库事务回滚时清理。批量调整目录必须提交每篇文章的 `expectedVersion`，任一冲突则整批回滚。

### Markdown 图片 / OSS

- 默认推荐 Bucket 私有。若部署明确使用“公共读、禁止公共写”，设置 `OSS_PUBLIC_READ=true` 使新对象继承 Bucket ACL；无论 ACL 如何，前端正文仍只保存稳定的 `/api/v1/markdown-images/{id}`，不要返回 AccessKey、对象键或可过期签名 URL。
- 上传必须继续限制 20 MB，并根据文件字节签名识别 PNG/JPEG/GIF/WebP；不要信任客户端文件名或 `Content-Type`，不接受 SVG/HTML。
- OSS 对象键由受控前缀、ownerId、按 `OSS_IMAGE_TIME_ZONE` 生成的 `yyyy/MM/dd` 日期路径、安全规范化后的原文件名主体和服务端 UUID 组成。必须移除客户端路径、原扩展名和危险字符并限制长度，最终扩展名只能来自文件字节签名。
- OSS 凭证只从后端环境变量读取。默认 `OSS_ENABLED=false` 时其他业务必须仍可启动，上传返回清晰的服务不可用错误。
- 调整图片接口时同步检查 `docs/MARKDOWN_IMAGES.md`、`.env.example`、`compose.yml`、multipart 上限和前端 Nginx/客户端超时。

## 测试与验证

- 业务规则放在最接近的现有 Service 测试中；账号隔离、JWT、HTTP 状态和响应头使用 Spring/MockMvc 集成测试。
- 快速数据库测试使用独立 H2 内存库、PostgreSQL 模式、`ddl-auto=create-drop` 和 `flyway.enabled=false`；真实 PostgreSQL 测试使用独立空库和 Flyway，不要复用开发或生产数据库。
- 外部 OSS 使用 `ImageStorage` 模拟，不在自动测试中使用真实 Bucket 或凭证。
- 接口变更至少测试：正常路径、参数校验、未登录、其他账号、资源不存在和相关失败清理。
- 完成修改前至少运行：

```powershell
mvn -B -ntp test
mvn -B -ntp package
```

- 不要手工编辑或提交 `target/`、日志、IDE 文件、`.env` 或 `application-local.yml`。

## 配置、文档与部署

- 配置入口为 `application.yml` + 环境变量；示例维护在 `.env.example` 和 `application-local.example.yml`。不要让 Spring Boot 自动读取包含真实密钥的仓库文件。
- 新增/修改端点时更新 `docs/API.md`；Markdown 契约更新 `docs/MARKDOWN_API.md`，图片/OSS 更新 `docs/MARKDOWN_IMAGES.md`。
- Docker 构建先运行 Maven package，再以非 root 用户启动 JAR；不要绕过 Dockerfile 中的测试/打包和健康检查。
- `compose.yml` 位于后端仓库，但会构建同级前端目录。跨端改动需要同时验证前端 `npm test && npm run build` 和后端 Maven 测试。
- 上线前确认：PostgreSQL 不公开暴露、数据库连接启用 TLS、JWT/数据库/OSS 密钥已轮换、CORS 限制到真实域名、Bucket ACL 与 `OSS_PUBLIC_READ` 一致且 RAM 权限最小化；公共读场景仍必须禁止公共写。

## PostgreSQL 初始化与数据导入

- 项目只保留 PostgreSQL 配置、驱动、建表脚本和部署服务。操作步骤在 `docs/POSTGRESQL_SETUP.md`：后端启动由 Flyway 建表，再手动执行业务数据 DML。
- 业务数据 DML 保存在 Git/Docker 排除的 `backups/` 中，不包含 DDL 或 Flyway 历史。导入前检查业务表为空，在同一事务中插入并校验，重复执行不得覆盖数据。用户提供的原始备份不得修改。
- 真实 PostgreSQL 测试使用 `TEST_POSTGRES_*`；仅指向可丢弃的隔离测试服务，CI 必须执行。新增 PostgreSQL 数据库行为必须在真实 PostgreSQL 上验证。
