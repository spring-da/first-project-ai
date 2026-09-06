# DevNest Backend

DevNest Android 与 Web 共用的 REST API 后端，由 springda 的个人 Flutter 项目演进而来。

## 技术栈

- Java 26
- Spring Boot 4.1.0
- Spring Web MVC
- Spring Data JPA / Hibernate
- Spring Security 7 + JWT（HS256）
- MySQL 8 / MySQL Connector/J
- Flyway 数据库版本管理
- Maven

Spring Boot 4.1.0 官方支持 Java 17–26，项目使用当前最新 Java 26。生产项目如果更重视长期支持，也可以将 `pom.xml` 的 `java.version` 改成 `25` 并安装 JDK 25 LTS。

## 已实现功能

- 一次性邀请链接注册、邮箱登录、全设备主动注销及 BCrypt(12) 密码散列
- 管理员人员管理、模拟登录成员完整工作区、管理员操作审计、短期随机临时密码、JWT 会话撤销和登录失败锁定
- 管理员系统公告发布与撤回、成员单次已读提示
- 成员意见交流、回复折叠分页、图片附件和管理员内容管理
- JWT 无状态认证
- 按登录用户隔离所有业务数据
- 带日期、截止时间、优先级、完成时间和归档状态的任务计划 CRUD
- 项目与技术栈 CRUD
- 知识领域目录 CRUD，以及片段/日志归类
- Markdown 文章 CRUD、批量导入和 ZIP 批量导出
- Markdown 图片上传、私有 OSS 存储及按账号鉴权读取（[配置说明](docs/MARKDOWN_IMAGES.md)）
- 代码片段 CRUD
- 结构化开发日志与标签 CRUD
- 开发者资料查询与修改
- 参数校验和统一 `ProblemDetail` 错误响应
- MySQL 8 表结构、索引和 Flyway 迁移
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
└── db/migration/  # V1 基础结构、V2–V14 增量迁移
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

## 2. 创建 MySQL 8 数据库

使用管理员账号执行：

```text
sql/mysql8/00_create_database.sql
```

项目启动时，Flyway 会自动执行：

```text
src/main/resources/db/migration/V1__init_schema.sql
```

后续版本会继续按顺序执行增量迁移，其中 V7 引入管理员人员管理，V8 加固一次性邀请、临时密码、登录锁定和 JWT 会话撤销，V9 扩展任务计划，V12 保存管理员操作审计记录，V13 修正审计状态字段类型，V14 增加系统公告、成员已读状态和意见交流消息。

> V8 上线提示：迁移后，V8 之前签发的 JWT 因不含 `auth_version` 会统一失效，用户需要重新登录；V7 中尚未注册的旧邀请没有可交付的一次性 Token，管理员需要在人员管理页为这些邮箱重新生成邀请链接。

如果你希望手动创建所有表，也可以在 `devnest` 数据库中直接执行该 Flyway SQL 文件。配置中的 `baseline-on-migrate` 可以兼容已手动建表的开发数据库。

## 3. 配置数据库与 JWT

推荐在 IDEA 的 Run Configuration → Environment variables 中填写：

```text
DB_URL=jdbc:mysql://127.0.0.1:3306/devnest?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true
DB_USERNAME=devnest
DB_PASSWORD=你的数据库密码
JWT_SECRET_BASE64=你的Base64密钥
```

全新空数据库首次启动时还必须临时配置初始管理员：

```text
BOOTSTRAP_ADMIN_EMAIL=你的管理员邮箱
BOOTSTRAP_ADMIN_PASSWORD=至少16位的高强度随机密码
```

默认 `BOOTSTRAP_ADMIN_REQUIRED=true`，所以空数据库没有同时配置这两个变量时后端会拒绝启动并给出明确错误。只有数据库完全没有账号时才会创建该管理员。首次登录会强制改密；改密完成后立即从部署环境删除这两个变量，后续启动不会再次创建或提升管理员。

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
- 云 MySQL 不应向公网开放 `3306`，只允许后端服务器访问。
- 生产数据库连接应启用 TLS，并删除 URL 中的 `useSSL=false`。
- 将 CORS 来源限制为真实网站域名。
- 使用最小权限 MySQL 应用账号，不要使用 `root`。
- 为数据库配置备份、监控和恢复演练。

## 下一阶段

- Flutter `RemoteAppRepository` 对接 REST API
- Refresh Token
- 分页、搜索和增量同步接口
- 头像文件上传
- OpenAPI/Swagger 文档
