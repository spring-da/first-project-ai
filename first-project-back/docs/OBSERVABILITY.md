# 可观测性与线上排障

## 请求关联

- 后端为每个响应返回 `X-Request-ID`，并将同一个值写入 MDC 日志字段 `requestId`。
- 调用方可以传入由字母、数字、点、下划线和短横线组成的 `X-Request-ID`，最长 128 字符；不安全或超长的值会被替换为服务端 UUID，避免日志注入。
- 统一异常处理生成的 RFC Problem Details 包含 `requestId`；安全过滤器提前拒绝的请求仍可从响应头取得同一编号。Web 客户端会在 5xx 提示中展示该编号，线上排障时可直接按编号检索日志。
- 日志不记录查询参数、JWT、密码、OSS 凭证或请求正文。

## 健康探针

| 端点 | 用途 | 认证 |
| --- | --- | --- |
| `/actuator/health/liveness` | 判断 JVM/应用是否仍可运行；失败时可重启容器 | 无 |
| `/actuator/health/readiness` | 判断应用及数据库是否可接收流量 | 无 |
| `/actuator/health` | 汇总状态，不公开组件详情 | 无 |

Docker 镜像使用 readiness 作为健康检查。不要用 liveness 检查数据库，否则数据库短暂抖动会造成应用反复重启。

## 指标

`PROMETHEUS_ENABLED=true` 时提供 `/actuator/prometheus`。该端点和 `/actuator/metrics/**` 仅允许已启用的管理员账号访问；不要通过公网 Nginx 匿名暴露。

Prometheus 可使用定期轮换的管理员 Bearer Token 抓取：

```yaml
scrape_configs:
  - job_name: devnest-backend
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["backend:8080"]
    authorization:
      type: Bearer
      credentials_file: /run/secrets/devnest-monitoring-token
```

建议至少监控：

- `http_server_requests_seconds`：5xx 比例、P95/P99 延迟、接口吞吐。
- `hikaricp_connections_active` 与 `hikaricp_connections_pending`：连接池耗尽。
- `jvm_memory_used_bytes`、GC 暂停和进程 CPU。
- readiness 连续失败、容器重启次数和 PostgreSQL 容量。

初始告警阈值可设为：5 分钟 5xx 比例超过 2%、P95 超过 2 秒、数据库等待连接持续大于 0。上线收集一周基线后再调整，避免无效告警。

## 故障处理顺序

1. 使用用户界面的请求编号定位日志，确认状态码和耗时。
2. 查看 readiness、5xx 比例和数据库连接池指标，区分应用、数据库或下游 OSS 故障。
3. 若涉及数据恢复，先停止写入并保留当前数据库快照；按 `POSTGRESQL_SETUP.md` 的隔离库流程验证备份，不直接在生产库试恢复。
4. 修复后验证健康探针、关键 API 与错误率恢复，再解除流量限制。
