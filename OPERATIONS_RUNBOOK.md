# TravelFlow 运维手册（Runbook）

本文档用于线上日常运维，基于当前部署结构：

- 应用目录：`/opt/travelflow`
- 前端静态目录：`/var/www/travelflow`
- 后端服务：`travelflow.service`
- 依赖容器：`travel-mysql`、`travel-redis`、`travel-qdrant`
- 环境变量：`/etc/travelflow/travelflow.env`
- Nginx 站点：`/etc/nginx/sites-available/travelflow-ip`

## 1. 快速定位命令

```bash
pwd
cd /opt/travelflow
ls -la
```

```bash
systemctl status travelflow --no-pager
docker ps
nginx -t
```

## 2. 常用运维命令

### 2.1 服务重启

```bash
systemctl restart travelflow
systemctl reload nginx
docker-compose -f /opt/travelflow/docker-compose.yml restart mysql redis qdrant
```

### 2.2 查看日志

```bash
journalctl -u travelflow -f
tail -f /var/log/nginx/access.log
tail -f /var/log/nginx/error.log
docker logs -f travel-mysql
docker logs -f travel-redis
docker logs -f travel-qdrant
```

### 2.3 查看端口监听

```bash
ss -lntp | grep -E ':80|:8080|:3306|:6379|:6333|:6334'
```

## 3. 发布更新流程（运维版）

最简方式（本地执行）：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\one-click-update.ps1
```

## 3.1 仅前端更新

```bash
cd /opt/travelflow/frontend
npm ci
npm run build
rm -rf /var/www/travelflow/*
cp -R dist/. /var/www/travelflow/
systemctl reload nginx
```

## 3.2 仅后端更新

```bash
cd /opt/travelflow
mvn -pl demo-app -am -DskipTests package
cp demo-app/target/*.jar demo-app/target/app.jar
systemctl restart travelflow
```

## 3.3 前后端一起更新

```bash
cd /opt/travelflow/frontend
npm ci && npm run build
rm -rf /var/www/travelflow/*
cp -R dist/. /var/www/travelflow/

cd /opt/travelflow
mvn -pl demo-app -am -DskipTests package
cp demo-app/target/*.jar demo-app/target/app.jar
systemctl restart travelflow
systemctl reload nginx
```

## 4. 环境变量变更流程

1. 修改文件：`/etc/travelflow/travelflow.env`
2. 重启后端：

```bash
systemctl restart travelflow
journalctl -u travelflow -n 100 --no-pager
```

重点变量：
- `DASHSCOPE_API_KEY`
- `APP_JWT_SECRET`
- `APP_CORS_ALLOWED_ORIGINS`
- `QDRANT_URL`

## 5. 日常巡检清单

每天建议检查：

1. 进程状态：
```bash
systemctl is-active travelflow
docker ps --format 'table {{.Names}}\t{{.Status}}'
```

2. 站点可用性：
```bash
curl -I http://127.0.0.1
curl -i http://127.0.0.1/api/v1/health
```

3. 资源水位：
```bash
top -b -n 1 | head -n 20
free -h
df -h
```

## 6. 常见故障与处理

## 6.1 `Permission denied (publickey)`

原因：SSH 仅允许密钥登录。  
处理：确认本机 `-i` 指向正确私钥，并检查服务器 `~/.ssh/authorized_keys`。

## 6.2 `docker-compose pull` 超时

原因：到 Docker Hub 网络波动。  
处理：配置 `/etc/docker/daemon.json` 的 `registry-mirrors` 后重启 Docker。

## 6.3 页面能开但 API `502`

原因：后端尚未启动完成或后端服务挂掉。  
处理：

```bash
systemctl status travelflow --no-pager
journalctl -u travelflow -n 200 --no-pager
```

## 6.4 API 返回 `401`

在当前项目里，很多接口默认需要登录态，`401` 不一定是故障。  
先区分：
- Nginx 502/504：基础链路故障
- 业务 401：鉴权逻辑生效

## 6.5 改完配置 Nginx 无法重载

```bash
nginx -t
```

先修复语法错误，再：

```bash
systemctl reload nginx
```

## 7. 数据备份建议

## 7.1 MySQL 备份

```bash
mkdir -p /opt/backup
docker exec travel-mysql mysqldump -uroot -proot --databases travel_ai > /opt/backup/travel_ai_$(date +%F_%H%M%S).sql
```

## 7.2 关键配置备份

```bash
cp /etc/travelflow/travelflow.env /opt/backup/travelflow.env.$(date +%F_%H%M%S)
cp /etc/nginx/sites-available/travelflow-ip /opt/backup/travelflow-ip.$(date +%F_%H%M%S).conf
```

## 8. 回滚方案（最小可用）

1. 保留上一个可用 Jar，例如 `app.prev.jar`。
2. 回滚命令：

```bash
cp /opt/travelflow/demo-app/target/app.prev.jar /opt/travelflow/demo-app/target/app.jar
systemctl restart travelflow
```

3. 前端回滚可使用上一版 `dist` 备份目录覆盖 `/var/www/travelflow`。

## 9. 计划买域名后补做

1. 域名 A 记录指向 `47.114.91.243`（`@` 和 `www`）。
2. 开放 `443` 端口。
3. 安装证书并开启 HTTPS（`certbot --nginx`）。
4. 把 `APP_CORS_ALLOWED_ORIGINS` 改为正式域名并重启后端。
