# 旅迹 TravelFlow（Monorepo）

本仓库包含一个「AI 旅行决策平台」的前后端工程：

- `demo-app`：Spring Boot 后端（JPA + MySQL + Redis + JWT）
- `frontend`：Vite + Vue 3 + TypeScript 前端

---

## 目录结构

- `demo-app/`：后端服务代码
- `frontend/`：前端页面与组件
- `docker-compose.yml`：本地依赖（MySQL / Redis）
- `scripts/`：辅助脚本

---

## 本地启动

### 0）一键启动（推荐）

在仓库根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-project.ps1
```

或直接双击：

```text
scripts\start-project.bat
```

### 1）启动依赖服务（推荐）

在仓库根目录执行：

```bash
docker compose up -d
```

### 2）启动后端

进入 `demo-app` 后，使用 IDEA 直接运行 `DemoApplication`，或执行：

```bash
mvn spring-boot:run
```

默认端口：`http://localhost:8080`

### 3）启动前端

进入 `frontend` 后执行：

```bash
npm install
npm run dev
```

默认端口：`http://localhost:5173`

---

## 主要能力

- AI 行程流式生成（SSE）
- 行程保存 / 恢复 / 删除（`/api/v1/trips`）
- 登录注册与权限（用户 / 管理员）
- 管理员卡片内容管理（新增 / 编辑 / 删除 / 启用）
- Markdown 结果渲染与追问

---

## 常见问题

### 1）MySQL 连接失败

- 检查 MySQL 是否启动
- 检查 `demo-app/src/main/resources/application.yml` 中的数据源配置

### 2）保存行程报 `Data too long for column 'answer_text'`

已在代码中将字段升级为 `LONGTEXT`，重启后端后会自动执行字段修复。

### 3）接口 401 / 403

- 先确认已登录并携带 JWT
- 管理接口仅管理员可访问（`/api/admin/**`）

---

## 构建命令

前端构建：

```bash
cd frontend
npm run build
```

后端构建：

```bash
cd demo-app
mvn -DskipTests package
```

---

## 生产部署（`www.smarttravel.cn`）

### 1）服务器准备（Ubuntu）

```bash
sudo apt update
sudo apt install -y nginx openjdk-17-jdk maven nodejs npm docker.io docker-compose-plugin certbot python3-certbot-nginx
sudo systemctl enable --now docker nginx
```

### 2）上传项目并执行一键部署

```bash
cd /opt
git clone <你的仓库地址> travelflow
cd /opt/travelflow
sudo bash scripts/deploy-smarttravel.sh smarttravel.cn admin@smarttravel.cn
```

脚本会自动完成：

- 启动 `mysql / redis / qdrant`
- 构建并发布前端到 `/var/www/travelflow`
- 构建后端并注册 `systemd` 服务 `travelflow`
- 配置 `nginx` 反向代理 `/api`
- 申请并启用 `smarttravel.cn` + `www.smarttravel.cn` HTTPS 证书

### 3）首次执行前要填环境变量

脚本首次运行会自动生成 `/etc/travelflow/travelflow.env`，请至少填写：

- `DASHSCOPE_API_KEY`
- `APP_JWT_SECRET`

模板见：`scripts/travelflow.env.example`
