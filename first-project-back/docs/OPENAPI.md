# OpenAPI 契约

DevNest 通过应用运行时生成 OpenAPI JSON，而不是维护一份容易与 Controller 漂移的静态副本。

## 本地查看

在受控本地环境设置：

```properties
OPENAPI_ENABLED=true
```

启动后访问 `http://localhost:8080/v3/api-docs`。该端点默认关闭，生产环境保持
`OPENAPI_ENABLED=false`。当前只引入 API JSON，不提供 Swagger UI，以缩小部署暴露面。

契约中的 `bearerAuth` 表示标准 `Authorization: Bearer <JWT>`。注册、登录、昵称可用性检查和
`/api/v1/public/**` 明确标记为公开，其余业务接口继承 Bearer 要求。

## 防漂移检查

`ApiBoundaryIntegrationTest` 会在 CI 中临时开启契约端点并检查：

- 登录与公开分享端点没有错误地声明鉴权；
- 任务等私有端点声明 `bearerAuth`；
- 分享令牌复制等 Web 客户端关键路径仍然存在；
- JWT audience、安全响应头和无凭据 CORS 策略有效。

修改 Controller 路径、HTTP 方法或认证规则时，必须同步更新 Web/Android 调用端、
`docs/API.md` 和这组契约断言。
