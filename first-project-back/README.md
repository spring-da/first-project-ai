# DevNest Backend

DevNest Android 与 Web 共用的 REST API 后端，由 springda 的个人 Flutter 项目演进而来。

## 技术栈

- Java 26
- Spring Boot 4.1.0
- Spring Web MVC
- Spring Data JPA / Hibernate
- Spring Security 7 + JWT（HS256）
- PostgreSQL 17+ / PostgreSQL JDBC，pgvector 0.8.2
- Flyway 数据库版本管理
- Maven

Spring Boot 4.1.0 官方支持 Java 17–26，项目使用当前最新 Java 26。生产项目如果更重视长期支持，也可以将 `pom.xml` 的 `java.version` 改成 `25` 并安装 JDK 25 LTS。

## 已实现功能

- 一次性邀请链接注册、注册昵称查重、邮箱登录、全设备主动注销及 BCrypt(12) 密码散列
- 管理员人员管理、模拟登录成员完整工作区、管理员操作审计、短期随机临时密码、JWT 会话撤销和登录失败锁定
- 管理员 Markdown 系统公告发布与撤回、成员单次已读提示
- 独立的系统公告与意见交流入口、回复折叠分页、链接识别、图片附件和管理员内容管理
- Markdown 文章、代码片段和开发日志的限时公开只读分享与随时撤销
- JWT 无状态认证
- 按登录用户隔离所有业务数据
- 带日期、截止时间、优先级、完成时间和归档状态的任务计划 CRUD
- 项目与技术栈 CRUD
- 知识领域目录 CRUD，以及片段/日志归类
- Markdown 文章 CRUD、批量导入和 ZIP 批量导出
- Markdown 图片上传、私有 OSS 存储及按账号鉴权读取（[配置说明](docs/MARKDOWN_IMAGES.md)）
- 代码片段 CRUD
- 结构化开发日志与标签 CRUD
- 开发者资料查询与修改、性别设置、默认头像与私有 OSS 头像上传
- 参数校验和统一 `ProblemDetail` 错误响应
- PostgreSQL 表结构、索引和 Flyway 迁移
- Flutter Web 本地开发所需 CORS 配置
- Actuator 健康检查

接口明细参见 [docs/API.md](docs/API.md)。

## 项目结构

```text
src/main/java/com/springda/devnest/
├── auth/          # 注册、登录和 JWT 签发
├── common/        # 基础实体和统一异常
├── config/        # Security、CORS 与应用配置
├── user/          # 用户认证实体
├── profile/       # 开发者资料
├── task/          # 今日任务
├── project/       # 项目雷达
├── knowledge/     # 知识领域目录
├── markdown/      # Markdown 文章、导入与导出
├── announcement/  # 系统公告与成员已读状态
├── community/     # 意见消息、回复与图片附件
├── snippet/       # 代码片段
└── log/           # 开发日志

src/main/resources/
├── application.yml
├── application-local.example.yml
└── db/postgresql/ # PostgreSQL V17 基线；后续从 V18 递增
```

每个功能包内部按 `Controller → Service → Repository → Entity` 分层。

## 1. 准备 JDK

请先阅读并执行 [JDK 26 Windows 安装教程](docs/JDK_26_WINDOWS_INSTALL.md)。

验证：

```powershell
java -version
javac -version
mvn -version
```

## 2. 准备 PostgreSQL

本项目只使用 PostgreSQL。已有数据的操作步骤见 [PostgreSQL 部署与数据导入](docs/POSTGRESQL_SETUP.md)：后端启动自动建表，再通过 DataGrip 执行业务数据 DML。

复制 `.env.example` 为 `.env`，配置独立 PostgreSQL 管理员密码、应用数据库密码、JWT 和 CORS，再启动数据库：

```bash
docker compose up -d postgres
```

首次创建数据卷时，`sql/postgresql/10-init.sh` 安装 `vector` 扩展并创建普通应用角色。后端启动后 Flyway 自动执行 `db/postgresql/V17__init_postgresql.sql`，JPA 仅校验表结构。已有非空数据库不会自动 baseline。

PostgreSQL 使用持久化 `postgres-data` 卷。以后集成 RAG 时再根据 embedding 模型增加分块、向量列及 HNSW 索引。

## 3. 配置数据库与 JWT

推荐在 IDEA 的 Run Configuration → Environment variables 中填写：

```text
DB_URL=jdbc:postgresql://127.0.0.1:5432/devnest
DB_USERNAME=devnest
DB_PASSWORD=你的数据库密码
JWT_SECRET_BASE64=你的Base64密钥
```

默认 `BOOTSTRAP_ADMIN_REQUIRED=false`，允许空库启动后手动导入已有账号，此时初始管理员邮箱和密码保持为空。如果完全从零使用、不导入数据，可临时配置初始管理员：

```text
BOOTSTRAP_ADMIN_EMAIL=你的管理员邮箱
BOOTSTRAP_ADMIN_PASSWORD=至少16位的高强度随机密码
```

只有数据库完全没有账号时才会创建该管理员。首次登录会强制改密；改密完成后从部署环境删除这两个变量，后续启动不会再次创建或提升管理员。

生成安全的 JWT 密钥：

```powershell
$bytes = New-Object byte[] 48
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

也可以复制：

```text
src/main/resources/application-local.example.yml
```

为：

```text
src/main/resources/application-local.yml
```

填写配置后，在 IDEA 中启用 Spring Profile：

```text
local
```

`application-local.yml` 已被 `.gitignore` 排除，不会意外提交密码。

## 4. IDEA 启动

1. IDEA → Open。
2. 选择本项目的 `pom.xml` 或项目根目录。
3. Maven 首次导入依赖。
4. Project SDK 选择 JDK 26。
5. Maven Runner JRE 也选择 JDK 26。
6. 运行 `DevNestBackendApplication`。

启动成功后访问：

```text
http://localhost:8080/actuator/health
```

应该返回：

```json
{"status":"UP"}
```

## Maven 命令

```powershell
mvn clean test
mvn spring-boot:run
mvn clean package
```

## Flutter / Web 对接

客户端调用：

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
```

`POST /api/v1/auth/register` 不再接收邮箱，必须提交管理员创建的一次性 `invitationToken`。完整契约见 [docs/API.md](docs/API.md)。

取得 `accessToken` 后，请在后续请求加入：

```http
Authorization: Bearer <accessToken>
```

Android 模拟器访问宿主机后端通常使用：

```text
http://10.0.2.2:8080
```

Flutter Web 本地开发地址需要加入 `CORS_ALLOWED_ORIGINS`。

## 上线前检查

- 必须替换默认 `JWT_SECRET_BASE64`。
- 不要提交数据库密码或 `application-local.yml`。
- PostgreSQL 不应向公网开放 `5432`，只允许后端服务器访问。
- 外部生产数据库连接使用 TLS（`sslmode=verify-full`）并配置可信 CA。
- 将 CORS 来源限制为真实网站域名。
- 使用普通 PostgreSQL 应用账号，不要使用 `postgres` 超级用户。
- 为数据库配置备份、监控和恢复演练。

## 下一阶段

- Flutter `RemoteAppRepository` 对接 REST API
- Refresh Token
- 分页、搜索和增量同步接口
- OpenAPI/Swagger 文档
