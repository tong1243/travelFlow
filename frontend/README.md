# Travel Assistant Frontend (Vite + Vue + TypeScript)

独立前端工程，配套后端 `demo-app` 使用。

## 开发启动

```powershell
cd "D:\Java agent\frontend"
npm.cmd install
npm.cmd run dev
```

默认地址：

- `http://localhost:5173`

## API 代理

开发环境通过 Vite 代理转发 `/api/*` 到后端：

- 默认目标：`http://localhost:8080`
- 可通过环境变量覆盖：`VITE_API_TARGET`

示例：

```powershell
$env:VITE_API_TARGET="http://localhost:18080"
npm.cmd run dev
```

## 构建

```powershell
npm.cmd run build
```

构建产物在 `frontend/dist`。
