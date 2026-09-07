# DevNest · 个人知识库

Vue 3 + TypeScript 前端和 Spring Boot + Java 26 后端，统一存放在此仓库。

- `first-project-web/`：工作台、任务、项目、Markdown、代码片段、开发日志、系统公告、意见交流、系统工具和人员管理。
- `first-project-back/`：JWT 认证、邀请注册、账号隔离、数据 API、系统公告、意见交流、历史版本、回收站、公开只读分享和 OSS 图片。

## 管理员管理成员数据

管理员进入 **人员管理 → 模拟登录 / 管理数据**，即可使用与正常成员相同的页面编辑该成员的完整工作区，包括禁用成员的数据。顶部会显示目标身份，点击“返回人员管理”退出。

后台每次检查管理员权限，业务数据仍按目标账号隔离；不需要成员密码，不会创建成员登录令牌。管理员与成员之间的文章并发修改继续使用版本冲突保护，人员管理页会显示账号管理和代管成员数据的审计记录。

## 本地运行

安装 Node.js 24、Java 26、Maven 和 PostgreSQL 17+（可带 pgvector）。配置后端环境变量请参考 [后端说明](first-project-back/README.md)；不要将真实配置或密钥提交到 Git。

```powershell
cd first-project-back
mvn spring-boot:run
```

另开终端运行前端：

```powershell
cd first-project-web
npm ci
npm run dev
```

前端默认将 `/api` 转发到本机 8080 端口。

## 验证

```powershell
cd first-project-web
npm test
npm run build
npm run test:ui:build
cd ../first-project-back
mvn -B -ntp verify
```

浏览器隔离回归方式见 [UI 测试说明](first-project-web/tests/ui/README.md)，使用合成数据，不访问真实账号或 OSS。

## 部署

部署与已有数据导入见 [PostgreSQL 操作步骤](first-project-back/docs/POSTGRESQL_SETUP.md)：启动项目自动建表，再用 DataGrip 执行转换后的业务数据 DML。

复制 `first-project-back/.env.example` 为同目录 `.env`，填写 PostgreSQL、JWT、CORS 和 OSS 配置。导入已有账号时保持初始管理员邮箱和密码为空，再运行：

```powershell
docker compose --project-directory first-project-back -f first-project-back/compose.yml up -d --build --remove-orphans
```

新数据库由 Flyway 执行 PostgreSQL V17 基线，包含现有全部业务表。Compose 启用 pgvector，为后续 RAG 向量检索提供数据库基础。前端访问端口为 8000。

接口契约见 [API 文档](first-project-back/docs/API.md)。
