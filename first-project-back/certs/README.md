# springda.top 的 HTTPS 证书

此目录已通过 Docker Compose **只读挂载**到前端 Nginx 容器的 `/etc/nginx/certs`。下载已签发的 **Nginx 格式**证书，将 PEM 编码的文件按下面的名字放入本目录：

| 服务器项目中的相对路径 | 内容 | 容器内路径 |
|---|---|---|
| `first-project-back/certs/fullchain.pem` | `springda.top` 的服务器证书及完整中间证书链，服务器证书在前 | `/etc/nginx/certs/fullchain.pem` |
| `first-project-back/certs/privkey.pem` | 与该证书配对、供 Nginx 无交互启动使用的未加密私钥 | `/etc/nginx/certs/privkey.pem` |

证书包中的原始文件名可以不同，确认内容后复制并重命名即可。不要将 ZIP、PFX 或 DER 文件仅改扩展名当作 PEM 使用。如果证书机构将服务器证书和中间证书分别提供，需要按机构说明组合为完整证书链。

**本目录刻意不包含证书或空白占位文件。文件缺失、格式错误或私钥不匹配时，Nginx 无法启动；请放齐真实文件后再部署。**

当前配置只提供 `https://springda.top`。80 端口仅将 `springda.top` 的请求跳转到 HTTPS；使用 IP 或其他主机名访问时直接断开连接（Nginx 内部状态码 `444`，浏览器不会收到正常网页）。443 端口仅接受 `springda.top` 的 TLS SNI，缺少 SNI 或使用其他名称时拒绝握手；握手后 HTTP Host 不匹配的请求也会被拒绝。暂未启用 `www.springda.top`；如需启用，应先确认 DNS 和证书均覆盖该域名，再调整 Nginx。

这些规则按请求中的主机名识别网站，域名仍然解析到服务器公网 IP。它们限制普通浏览器直接输入 IP 的访问，不会隐藏服务器 IP，也不能替代应用登录鉴权。

证书目录已从 Git 和后端 Docker 构建上下文中排除，文件不会随代码提交或构建进镜像。部署到服务器时需要单独放置，Linux 上建议将目录权限设为 `700`、私钥权限设为 `600`，并确保容器的 Nginx 主进程有读取权限。

## 放好文件后的部署步骤

以下命令在 **服务器原来的 `first-project-back` 部署目录**执行，沿用原有 Compose 项目名。仅修改现有 `.env` 的网站来源，保留原数据库、JWT 和 OSS 等配置：

```dotenv
CORS_ALLOWED_ORIGINS=https://springda.top
```

确认域名 A 记录指向服务器公网 IP，安全组和系统防火墙放行 TCP 80、443，并确认这两个端口没有被其他服务占用。

先构建新前端镜像，再校验 Nginx。`backend` 必须已经运行，Nginx 校验时需要解析该服务名：

```bash
docker compose build web
docker compose run --rm --no-deps web nginx -t
```

仅在校验通过后更新服务；`up` 会应用新的端口、挂载和 `.env`，单独 `restart` 不会应用这些 Compose 变更：

```bash
docker compose up -d --no-deps backend web
docker compose ps
curl -I http://springda.top
curl -I https://springda.top
```

通过域名访问 HTTP 应返回 `308` 并指向 HTTPS；HTTPS 应能通过证书校验并返回网站。直接访问 `http://服务器公网IP` 应断开连接，访问 `https://服务器公网IP` 应握手失败；这些是预期拒绝结果。检查登录、页面刷新和图片上传是否正常。若 `nginx -t` 失败，修正证书或配置后重新检查，再更新服务。

已部署 HTTPS、仅更新域名访问限制时，在同一部署目录执行以下命令，校验通过后再运行最后一条：

```bash
docker compose build web
docker compose run --rm --no-deps web nginx -t
docker compose up -d --no-deps web
```

证书续期后替换本目录下同名文件，在运行中的容器先校验再重载，无需重建镜像：

```bash
docker compose exec web nginx -t
docker compose exec web nginx -s reload
```
