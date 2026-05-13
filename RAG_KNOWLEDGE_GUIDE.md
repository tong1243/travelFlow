# RAG 知识库使用说明

## 1. 用户上传自己的知识库

接口：`POST /api/v1/knowledge/documents/upload`

- 需要 `Bearer Token`
- 支持文件类型：`txt / md / markdown / csv / json / xml / yaml / yml / log / pdf / docx`
- 后端会自动解析内容、切分分片、写入 MySQL，并同步向量到 Qdrant

示例（PowerShell）：

```powershell
$base = "http://127.0.0.1:8080"
$token = "<your-jwt-token>"

curl.exe -X POST "$base/api/v1/knowledge/documents/upload" `
  -H "Authorization: Bearer $token" `
  -F "file=@D:/data/my-knowledge.docx" `
  -F "title=我的知识库" `
  -F "sourceType=user_upload" `
  -F "sourceRef=D:/data/my-knowledge.docx"
```

## 2. 一键生成热门旅游景点知识库（写入当前用户）

接口：`POST /api/v1/knowledge/documents/seed/popular-attractions?overwrite=false`

- `overwrite=false`：已存在同一模板文档时跳过
- `overwrite=true`：覆盖更新已有模板文档

示例（PowerShell）：

```powershell
$base = "http://127.0.0.1:8080"
$token = "<your-jwt-token>"

Invoke-RestMethod -Method Post `
  -Uri "$base/api/v1/knowledge/documents/seed/popular-attractions?overwrite=false" `
  -Headers @{ Authorization = "Bearer $token" }
```

## 3. 快速脚本

项目内置脚本：`scripts/seed-popular-attractions.ps1`

```powershell
.\scripts\seed-popular-attractions.ps1 -RegisterIfMissing
```

可选参数：

- `-BaseUrl http://127.0.0.1:8080`
- `-Username rag_seed_user`
- `-Password 12345678`
- `-Overwrite`

## 4. 用户数据隔离说明

- 普通用户：只可查询/管理自己上传的知识库
- 系统共享文档（`createdBy = null`）对所有用户可读、仅管理员可改
- 管理员：可查看和管理全部知识文档
