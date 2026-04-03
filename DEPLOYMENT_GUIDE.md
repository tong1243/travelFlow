# TravelFlow 部署上线流程（IP 方案）

本文档基于当前项目实际部署方式整理，适用于先不买域名、直接通过公网 IP 上线。

- 服务器公网 IP：`47.114.91.243`
- 服务器系统：`Ubuntu 22.04`
- 项目目录：`/opt/travelflow`
- 前端目录：`/var/www/travelflow`
- 后端服务名：`travelflow`

## 1. 前置检查

1. 安全组放行端口：`22`、`80`（可选 `443`、`8080`）。
2. 已能通过密钥登录服务器：

```bash
ssh -i ~/.ssh/id_ed25519 root@47.114.91.243
```

## 2. 安装服务器依赖

```bash
apt update
apt install -y unzip git openjdk-17-jdk maven nodejs npm docker.io docker-compose nginx
systemctl enable --now docker nginx
```

## 3. 上传项目到服务器（无 GitHub 凭证方案）

### 3.1 在本机（Windows PowerShell）打包并上传

```powershell
Compress-Archive -Path "D:\Java agent\demo-app","D:\Java agent\frontend","D:\Java agent\scripts","D:\Java agent\docker-compose.yml","D:\Java agent\pom.xml","D:\Java agent\README.md","D:\Java agent\.gitignore" -DestinationPath "D:\travelflow.zip" -Force
scp -i C:\Users\Breeze\.ssh\id_ed25519 D:\travelflow.zip root@47.114.91.243:/opt/travelflow.zip
```

### 3.2 在服务器解压

```bash
rm -rf /opt/travelflow
mkdir -p /opt/travelflow
unzip -oq /opt/travelflow.zip -d /opt/travelflow
ls -la /opt/travelflow
```

## 4. 可选：配置 Docker 镜像加速（大陆网络建议）

```bash
cat > /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://docker.1ms.run",
    "https://hub-mirror.c.163.com"
  ]
}
EOF
systemctl restart docker
docker info | sed -n '/Registry Mirrors/,+6p'
```

## 5. 启动依赖容器

```bash
cd /opt/travelflow
docker-compose up -d mysql redis qdrant
docker ps
```

启动成功后应看到：
- `travel-mysql`
- `travel-redis`
- `travel-qdrant`

## 6. 配置后端环境变量

```bash
mkdir -p /etc/travelflow
cat > /etc/travelflow/travelflow.env <<'EOF'
DASHSCOPE_API_KEY=你的DashScopeKey
APP_JWT_SECRET=请替换为32位以上随机字符串
APP_CORS_ALLOWED_ORIGINS=http://47.114.91.243
APP_JWT_EXPIRE_SECONDS=86400
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
QDRANT_URL=http://127.0.0.1:6333
EOF
```

## 7. 构建并发布前端

```bash
cd /opt/travelflow/frontend
npm ci
npm run build
mkdir -p /var/www/travelflow
rm -rf /var/www/travelflow/*
cp -R dist/. /var/www/travelflow/
```

## 8. 构建后端 Jar

```bash
cd /opt/travelflow
mvn -pl demo-app -am -DskipTests package
cp demo-app/target/*.jar demo-app/target/app.jar
```

## 9. 配置 systemd 后端服务

```bash
cat > /etc/systemd/system/travelflow.service <<'EOF'
[Unit]
Description=TravelFlow Spring Boot Service
After=network.target docker.service
Requires=docker.service

[Service]
Type=simple
WorkingDirectory=/opt/travelflow
EnvironmentFile=/etc/travelflow/travelflow.env
ExecStart=/usr/bin/java -jar /opt/travelflow/demo-app/target/app.jar
Restart=always
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now travelflow
```

## 10. 配置 Nginx（IP 访问）

```bash
cat > /etc/nginx/sites-available/travelflow-ip <<'EOF'
server {
    listen 80 default_server;
    server_name _;
    root /var/www/travelflow;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 3600;
        proxy_send_timeout 3600;
        proxy_buffering off;
        gzip off;
    }
}
EOF

ln -sf /etc/nginx/sites-available/travelflow-ip /etc/nginx/sites-enabled/travelflow-ip
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl reload nginx
```

## 11. 验收

```bash
systemctl status travelflow --no-pager
docker ps
curl -I http://127.0.0.1
curl -i http://127.0.0.1/api/v1/health
```

说明：
- 首页 `200` 为正常。
- `/api/v1/health` 返回 `401` 在当前项目中也正常（接口受鉴权保护）。

浏览器访问：
- `http://47.114.91.243`

## 12. 首次上线后必做

1. 确保 `DASHSCOPE_API_KEY` 已填写并有效。
2. 重启后端服务：

```bash
systemctl restart travelflow
journalctl -u travelflow -f
```

## 13. 后续升级发布（重复流程）

```bash
cd /opt/travelflow
# 更新代码（按你的方式：git pull 或重新上传解压）

cd /opt/travelflow/frontend
npm ci
npm run build
rm -rf /var/www/travelflow/*
cp -R dist/. /var/www/travelflow/

cd /opt/travelflow
mvn -pl demo-app -am -DskipTests package
cp demo-app/target/*.jar demo-app/target/app.jar
systemctl restart travelflow
systemctl reload nginx
```

## 14. 一条命令更新（推荐）

已提供本地脚本：`scripts/one-click-update.ps1`

在 Windows PowerShell 项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\one-click-update.ps1
```

默认会自动执行：
1. 本地打包（排除 `.local` 等大目录）
2. 上传到服务器 `/opt/travelflow.zip`
3. 触发服务器脚本 `scripts/server-redeploy.sh`
4. 自动重建前后端并重启 `travelflow`
