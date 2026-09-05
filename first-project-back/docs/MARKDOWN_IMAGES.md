# Markdown 图片与阿里云 OSS 配置

## 使用方式

在网页的 Markdown 正文中粘贴截图（Ctrl+V / ⌘V）、拖入图片，或点击格式工具栏的图片按钮。支持 PNG、JPG、GIF、WebP，单张不超过 20 MB，同时最多 5 张；不接受 SVG/HTML。上传后插入 Markdown 图片语法，再用 Ctrl+S / ⌘S 保存文章。

上传中可以继续输入。上传期间会阻止保存和离开，并提示等待；上传完成后可再次操作，避免云端文章包含临时占位。本机草稿也不会持久化上传占位。失败时去掉失败占位，保留其他正文，可重新粘贴或选择文件重试。

## 1. 配置后端环境变量

将项目根目录 `.env.example` 中的 OSS 段填入部署用 `.env`。Docker Compose 会读取并传给后端容器；IDEA 或 `java -jar` 启动时，需将这些值作为进程环境变量注入（Spring Boot 不会自动读取 `.env` 文件）。不要提交真实 `.env`，也不要把密钥填写到前端 `VITE_*` 变量。

```dotenv
OSS_ENABLED=true
OSS_ENDPOINT=https://oss-cn-hangzhou.aliyuncs.com
OSS_REGION=cn-hangzhou
OSS_BUCKET=your-bucket
OSS_ACCESS_KEY_ID=your-ram-access-key-id
OSS_ACCESS_KEY_SECRET=your-ram-access-key-secret
OSS_SECURITY_TOKEN=
OSS_IMAGE_PREFIX=markdown/images
OSS_IMAGE_TIME_ZONE=Asia/Shanghai
OSS_PUBLIC_READ=false
```

Endpoint、Region 必须与实际 Bucket 对应，Endpoint 使用 HTTPS。使用 RAM 用户的专用凭证；若采用临时 STS 凭证，需要同时注入 `OSS_SECURITY_TOKEN` 并在到期前更新凭证、重启后端。默认 `OSS_ENABLED=false`，其他功能仍可启动，图片上传返回清晰的 503 配置提示。

对象键按账号和日期组织，默认使用北京时间生成日期路径。例如账号 `owner-id` 在 2026-08-29 上传 PNG 时，对象键格式为：

```text
markdown/images/owner-id/2026/08/29/项目架构图_服务端UUID.png
```

OSS 是扁平对象存储，这些目录由对象键中的 `/` 在控制台中模拟，无需提前手工创建。可通过 `OSS_IMAGE_PREFIX` 调整最前面的受控前缀，通过 `OSS_IMAGE_TIME_ZONE` 调整日期归属时区。对象文件名采用“规范化后的原文件名主体_UUID.真实扩展名”：后端会移除目录、原扩展名和危险字符并限制长度，最终扩展名始终来自文件字节签名，不信任客户端声明。

`OSS_IMAGE_PREFIX=markdown/images` 是正确配置，它只控制最前面的两级前缀。日期目录和“原文件名_UUID”由后端代码追加。升级前已上传的旧对象不会自动改名，因为数据库仍保存它们的完整对象键；只有部署新后端后创建的新对象使用上述格式。若新上传仍显示为 `markdown/images/账号UUID/对象UUID.ext`，说明运行实例没有使用最新 JAR/镜像，需要重新构建并重启后端，而不是修改 Prefix。

默认建议 Bucket 保持私有权限，此时保留 `OSS_PUBLIC_READ=false`，后端会把新对象显式设为 private。若部署已经明确采用“Bucket 公共读、禁止公共写”，设置 `OSS_PUBLIC_READ=true`，后端不再覆盖对象 ACL，而是让新对象继承 Bucket 的公共读权限。该模式下任何拿到对象 URL 的人都能读取图片，因此图片不得包含敏感内容；RAM 用户仍只能由后端持有，公共写必须关闭。应用正文始终保存稳定的内部图片 API，不保存 OSS 对象地址。采用官方 OSS Java SDK 的 V4 签名，参考 [阿里云 Java SDK 配置说明](https://www.alibabacloud.com/help/en/oss/developer-reference/oss-java-sdk/)。

`OSS_PUBLIC_READ=true` 本身不会把私有 Bucket 改成公共读，也不会提升应用代理读取速度；它只决定新对象是否显式覆盖为 private。除非确实需要直接公开对象 URL，否则建议使用默认的 `false`。

## 性能与地域

- 浏览器仍通过稳定的鉴权图片 API 访问，不暴露 OSS 对象键或签名 URL。后端现以 64 KiB 分块把 OSS 响应流式转发，避免等待整张图片读入内存后才开始响应。
- 新上传的本地 `File` 会直接进入当前会话的 64 MiB LRU 预览缓存，不再上传完成后马上从 OSS 下载一次；同一会话内切换编辑、双栏和阅读模式也会复用缓存。多图批次并发上传，仍最多接受 5 张。
- 登录用户的不可变图片响应允许浏览器私有缓存 1 小时，并按 `Authorization` 隔离；公开分享图片继续 `no-store`，以保证撤销或到期后及时失效。OSS 新对象同时写入一年不可变缓存元数据，供明确采用公共读/CDN 的部署复用。
- 如果后端部署在阿里云 ECS/容器服务，并且与 Bucket 同在上海地域，可把 `OSS_ENDPOINT` 改为 `https://oss-cn-shanghai-internal.aliyuncs.com` 使用内网；只有同地域阿里云网络可以使用 internal Endpoint。后端不在阿里云上海时继续使用外网 Endpoint，远距离链路再评估 OSS 传输加速。
- 网站图片应继续使用标准存储。LRS 与 ZRS 主要影响持久性和可用性，并不是截图中秒级等待的主要原因；ZRS 可保留。不要把经常预览的图片转为归档、冷归档或深度冷归档。

## 2. RAM 权限范围

为应用专用 RAM 用户配置目标 Bucket 图片前缀的读写权限，不要使用主账号 AccessKey。以下策略中的 Bucket 名和前缀需与环境变量一致：

```json
{
  "Version": "1",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["oss:PutObject", "oss:PutObjectAcl", "oss:GetObject", "oss:DeleteObject"],
    "Resource": ["acs:oss:*:*:your-bucket/markdown/images/*"]
  }]
}
```

`DeleteObject` 仅用于数据库记录写入失败后的上传清理；应用不会因为删除笔记而自动删除图片，以免破坏本机草稿和历史版本的引用。

## 3. 部署

- 后端升级后让 Flyway 执行 `V6__add_markdown_images.sql`；该表保存图片所属账号和 OSS 对象键，不保存密钥。
- 同时部署新的后端 JAR、前端 `dist` 和 Nginx 配置。仅替换前端无法启用图片接口。
- 项目 Nginx 配置已将 `client_max_body_size` 设为 `105m`，用于 20 MB 单图上传及最大 100 MB ZIP 导入；图片服务本身仍严格限制单图 20 MB。如还有外层反向代理，也要同步调整其请求体限制及上传超时。
- 前端只需已有的 `VITE_API_BASE_URL`；默认同域 `/api/v1` 通过 Nginx 转发，跨域部署需沿用后端 CORS 允许来源配置。
- 正式部署建议全站 HTTPS，避免登录令牌、正文和图片在客户端到后端之间以明文传输。

## 接口

`POST /api/v1/markdown-images`：JWT Bearer 认证，`multipart/form-data`，字段名 `file`。成功返回 201：

```json
{"id":"图片UUID","url":"/api/v1/markdown-images/图片UUID","contentType":"image/png","size":12345}
```

`GET /api/v1/markdown-images/{id}`：JWT Bearer 认证，只有图片所属账号可访问。后端从 OSS 流式返回图片，带 `private, max-age=3600, immutable`、`Vary: Authorization` 和 `nosniff` 响应头。其他账号返回 404；未登录返回 401。管理员代管成员工作区时会再次校验管理员状态和目标账号。文件缺失/格式错误返回 400，超过 multipart 上限返回 413，OSS 未配置或不可用返回 503。

`GET /api/v1/public/markdown-shares/{token}/images/{id}`：无需账号，但必须提供仍在有效期内且未撤销的文章分享 Token，并且图片 ID 必须被该分享文章的当前正文实际引用。响应使用 `no-store` 与 `nosniff`；无效链接、回收站文章、其他图片均返回 404。Token 不会写入正文，公开阅读页只在内存请求路径中使用它。

Markdown 存放稳定引用 `![说明](/api/v1/markdown-images/UUID)`，不存储 AccessKey、JWT 或会过期的 OSS 签名 URL。网页在预览、阅读及历史版本里自动用登录令牌读取图片，生成内存中的 Blob URL；原始 Blob 受 64 MiB LRU 上限约束，切换账号时会清理。

**可移植性**：ZIP 导出会把正文使用的托管图片写入 `assets/` 并把内部链接改成相对路径，因此下载后可离线查看；把该 ZIP 再导入时，相对图片会重新上传 OSS 并恢复成稳定内部引用。单独导入 `.md` 无法携带本地相对图片，应同时打包成 ZIP。不要给仍被正文或历史版本引用的图片设置自动到期删除规则。

## 验证范围

后端测试使用 H2 和模拟对象存储，覆盖 JWT、账号隔离、大小/文件签名检查、存储失败与数据库失败清理。前端隔离测试可模拟成功、失败和延迟上传。真实 Bucket 的连通性、RAM 权限与 HTTPS 部署需在填写真实配置后验证；测试不使用任何生产凭证或真实笔记。


管理员在成员工作区使用同一接口并携带 `X-Workspace-Owner`，服务端逐次验证管理员后按目标成员隔离读写；完整规则见 [API.md](API.md#管理员模拟登录与业务编辑)。文章写入与恢复仍要求正确的 `expectedVersion`。图片上传归属于目标成员，读取响应使用 `Vary: Authorization, X-Workspace-Owner`，客户端缓存也按工作区隔离。
