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

导入已有账号时，初始管理员邮箱和密码保持为空。默认允许空库启动；导入后直接使用备份中的账号和密码登录。JWT、OSS 等继续使用原部署的有效配置，`CORS_ALLOWED_ORIGINS` 填 `https://springda.top`。先按 [HTTPS 证书说明](../certs/README.md) 放齐证书和私钥，缺少文件时前端无法启动。删除旧数据库专用的环境变量，使用上面的 PostgreSQL 配置。Compose 会自动将后端的数据库主机设为 `postgres`；示例中的 `DB_URL` 用于在宿主机直接启动后端。

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

点击 **Test Connection** 成功后，刷新 `public`，应看到业务表（包含 `flowcharts` 与 `flowchart_revisions`）和 `flyway_schema_history`。

## 3. 导入历史业务 DML

以下 `devnest-data.sql` 是原始历史备份，保持原样。**当前 V19 不能直接导入含开发日志的旧 DML。** 请先用兼容 V18 的应用在可丢弃隔离库建表，在该库执行本节导入和行数核对，再按下方 V19 迁移步骤升级。不要在已经由当前应用初始化到 V19 的库中直接执行旧文件。

原始文件位于本地：

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

对应结果应为 **4、10、38、16**。这是隔离库中的历史导入核对；完成下方 V19 升级和应用切换后再开放业务访问。PostgreSQL 显示时间取决于客户端会话时区；如需显示北京时间，在 DataGrip 会话执行 `SET TIME ZONE 'Asia/Shanghai';`，不会改变保存的时间点。

图片和头像仍使用原 OSS Bucket；DML 保留其引用。数据文件只包含本次备份中的内容。文件含账号密码散列和正文，保存在不提交 Git、不进入 Docker 镜像的 `backups/` 目录。

## 从零使用时

如果不导入已有数据，可以在空库首次启动时填写 `BOOTSTRAP_ADMIN_EMAIL` 和 `BOOTSTRAP_ADMIN_PASSWORD` 创建初始管理员，密码为 16–72 位。首次登录改密后删除这两个环境变量；已有账号的库不会再次创建管理员。


## V19 流程图升级与恢复

V17/V18 保持不变；部署新版本时 Flyway 执行 V19。V19 清除开发日志（包括回收站）、日志标签、日志单项分享及共享池记录、集合中的日志项；仅含日志的集合保留记录并撤销，混合集合保留非日志项及原链接。新的流程图表和历史表按所有者、目录、版本及创建键约束建表。

升级前停止写入，使用 `pg_dump -Fc -f devnest-before-v19.dump <database>` 保存整个数据库（含 Flyway 历史），再用 `pg_restore --list devnest-before-v19.dump` 检查归档。必须将备份恢复到另一独立空库并核对日志、标签、旧单项链接和混合集合行数后才部署。不要把测试库或恢复库连接配置写回生产环境。

需要回退时停止新版本，先保留升级后数据库副本，将升级前备份恢复到独立数据库，验证业务及 Flyway V18 状态，然后用兼容 V18 的应用切换到恢复库。不要编辑历史迁移、手工伪造迁移历史或只恢复已删除的几张表；这无法恢复完整一致状态。

旧业务 DML 如包含 `dev_logs`、`dev_log_tags` 或日志分享记录，不能直接导入 V19。保留原始备份，在可丢弃的 V18 隔离库导入并校验后备份，再让 Flyway 升级到 V19；不要修改用户原始备份。

如需为新 V19 空库生成新的导入文件，在上述已升级、校验过的隔离库导出 `pg_dump --data-only --no-owner --no-privileges --exclude-table=public.flyway_schema_history -f devnest-data-v19.sql <staging_database>`。V19 已清理日志表及日志分享行，因此新文件只包含仍受支持的业务数据。目标先由当前应用建表，再停止业务写入并在空业务库事务中导入这份新文件；不得用旧 DML 覆盖它，也不得向目标导入源库 Flyway 历史。

测试使用独立库的 `TEST_POSTGRES_URL`、`TEST_POSTGRES_USERNAME`、`TEST_POSTGRES_PASSWORD` 运行 `mvn -B -ntp test`；覆盖真实并发创建/版本冲突、批量回滚、外键及分享失效。生产数据库不得用于此测试。

### 可重复的 V18 → V19 合成数据回归

`FlowchartMigrationPostgreSqlTest` 随启用了 `TEST_POSTGRES_URL` 的 Maven 测试运行。测试账号需要在**可丢弃测试集群**内创建数据库的权限（`CREATEDB`）；不依赖 `pg_dump`、`pg_restore` 或额外客户端工具。它只在本次随机生成的 `flowchart_upgrade_<UUID>_src/bak/restore` 数据库中执行迁移、造数和恢复，`TEST_POSTGRES_URL` 指定的数据库仅作为连接协调入口，不会被此测试清空或迁移。

测试从 V17/V18 建表，载入仓库内 `src/test/resources/flowchart-upgrade-v18-fixture.sql` 的合成内容：活跃/回收站日志、标签、旧单项链接、共享池、混合与仅日志集合、原已撤销集合、文章和历史、片段、审计事件。升级前通过 PostgreSQL `CREATE DATABASE ... TEMPLATE ...` 取得快照并逐表逐行校验；V19 执行后验证日志清除、日志集合撤销，以及文章、片段、旧有效链接、混合集合和审计历史保留。随后在第三个新库中从快照恢复并核对完整 V18 数据与 Flyway 校验和，再验证恢复库可重新升级。测试最终只删除自己创建的三个随机库。

这是**同一测试集群内的物理克隆恢复演练**，不是逻辑 `pg_dump` 备份，也没有验证异机/离线灾难恢复。它不能替代上文部署前的完整归档备份、独立恢复验证和持久化保管要求。
