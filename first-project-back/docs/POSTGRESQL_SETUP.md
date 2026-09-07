# PostgreSQL 部署与数据导入

流程只有三步：**配置并启动项目 → 后端自动建表 → 在数据库工具中执行 DML**。

## 1. 配置并启动

将本次修改后的项目代码部署到服务器，保留 `first-project-back` 与 `first-project-web` 同级目录结构。在原来的 `first-project-back` 部署目录，按新版 `.env.example` 更新 `.env`（首次部署才复制），填写 PostgreSQL、JWT、CORS 和 OSS 配置。数据库相关变量如下：

```dotenv
POSTGRES_DB=devnest
DB_USERNAME=devnest
DB_PASSWORD=你的PostgreSQL应用密码
POSTGRES_ADMIN_PASSWORD=你的PostgreSQL管理员密码
DB_URL=jdbc:postgresql://127.0.0.1:5432/devnest

BOOTSTRAP_ADMIN_REQUIRED=false
BOOTSTRAP_ADMIN_EMAIL=
BOOTSTRAP_ADMIN_PASSWORD=
```

导入已有账号时，初始管理员邮箱和密码保持为空。默认允许空库启动；导入后直接使用备份中的账号和密码登录。JWT、OSS 等继续使用原部署的有效配置，`CORS_ALLOWED_ORIGINS` 填实际网站地址（例如 `http://服务器IP:8000`）。删除旧数据库专用的环境变量，使用上面的 PostgreSQL 配置。Compose 会自动将后端的数据库主机设为 `postgres`；示例中的 `DB_URL` 用于在宿主机直接启动后端。

```bash
docker compose up -d --build --remove-orphans
```

沿用原来的 Compose 项目名运行；`--remove-orphans` 会移除这个项目已不再配置的旧数据库容器。Compose 只部署 PostgreSQL（含 pgvector）、后端和前端，无需在服务器另外安装 PostgreSQL。PostgreSQL 容器创建数据库、普通应用账号及 vector 扩展；**后端启动时 Flyway 自动创建全部业务表**，JPA 仅校验表结构。后续重启不会重建表或删除数据。

等服务启动成功：

```bash
curl --fail http://127.0.0.1:8080/actuator/health
```

看到 `{"status":"UP"}` 后即可导入。空库尚无账号，先完成下面的数据导入。

## 2. 使用 DataGrip 连接 PostgreSQL

在 DataGrip 中新增 **PostgreSQL** 数据源，填写：

| 项目 | 值 |
|---|---|
| Host | `127.0.0.1` |
| Port | `5432` |
| Database | `devnest`（与你的 POSTGRES_DB 一致） |
| User | `devnest`（与你的 DB_USERNAME 一致） |
| Password | DB_PASSWORD 的值 |
| Schema | `public` |

连接云服务器时，在 DataGrip 的 **SSH/SSL → Use SSH tunnel** 中填写你原有的服务器 SSH 连接；上面的 Host 仍填服务器视角的 `127.0.0.1`。Compose 的 5432 端口绑定服务器本机，通过 SSH 隧道访问即可。

点击 **Test Connection** 成功后，刷新 `public`，应看到 19 张业务表和 `flyway_schema_history`。

## 3. 执行数据 DML

已转换的文件位于本地：

```text
first-project-back/backups/postgresql/devnest-data.sql
```

桌面也有相同副本：`C:\Users\Administrator\Desktop\postgresql-dml\devnest-data.sql`。这次请使用 `devnest-data.sql`，它是配合后端自动建表的纯业务数据文件。

用 DataGrip 以 UTF-8 打开文件，选择上一步的数据库，在没有其他未提交事务的控制台中**全选并执行所有语句**，不要只执行光标所在的一条语句。导入完成前先不要在网页新增数据。

- DML 对应你提供的备份，共 19 张业务表、117 条记录；时间已按确认的 UTC+8 转换为 UTC 时间点。
- 只写入业务数据，不创建/修改表，也不写入 `flyway_schema_history`。
- 脚本自带事务、空表检查和导入后的数量校验。目标业务表必须为空，重复执行会报错并回滚，不会覆盖数据。
- 如果客户端报错后仍停留在失败事务中，执行 `ROLLBACK;`，解决原因后再执行整份文件。

执行成功后可核对：

```sql
SELECT 'users' AS table_name, count(*) AS rows FROM users
UNION ALL SELECT 'markdown_documents', count(*) FROM markdown_documents
UNION ALL SELECT 'markdown_document_revisions', count(*) FROM markdown_document_revisions
UNION ALL SELECT 'code_snippets', count(*) FROM code_snippets;
```

对应结果应为 **4、10、38、16**。现在访问 `http://服务器IP:8000`（或原网站域名），用原账号登录即可，后端无需重启。PostgreSQL 显示时间取决于客户端会话时区；如需显示北京时间，在 DataGrip 会话执行 `SET TIME ZONE 'Asia/Shanghai';`，不会改变保存的时间点。

图片和头像仍使用原 OSS Bucket；DML 保留其引用。数据文件只包含本次备份中的内容。文件含账号密码散列和正文，保存在不提交 Git、不进入 Docker 镜像的 `backups/` 目录。

## 从零使用时

如果不导入已有数据，可以在空库首次启动时填写 `BOOTSTRAP_ADMIN_EMAIL` 和 `BOOTSTRAP_ADMIN_PASSWORD` 创建初始管理员，密码为 16–72 位。首次登录改密后删除这两个环境变量；已有账号的库不会再次创建管理员。
