# API 接口清单与整改计划

## 对比结果（当前代码）

- 前端有、后端没有：`0`
- 后端有、前端没有：`20`

## 后端有前端无（按建议分组）

### A. 建议保留（非流式备用能力）

- `POST /api/travel/plan`
- `POST /api/portal/spot-plan`
- `POST /api/travel/budget`
- `POST /api/travel/plan/files`
- `POST /api/travel/file-qa`

### B. 建议前端扩展（高价值）

- `POST /api/files/upload`
- `GET/POST/PUT/DELETE /api/v1/knowledge/documents*`
- `POST /api/v1/knowledge/documents/upload`
- `GET /api/v1/chat/sessions`
- `GET /api/v1/chat/sessions/{sessionId}/messages`
- `POST /api/v1/chat/ask`

### C. 候选下线（需观察访问量后再删）

- `GET /api/portal/categories`
- `POST /api/v1/vector/embed`
- `POST /api/v1/vector/search`
- `POST /api/v1/vector/upsert`
- `GET /api/v1/users`
- `GET /api/v1/users/me`

## 本次已完成代码改动

- [x] 为候选接口添加 `@Deprecated(since = "2026-04", forRemoval = false)` 标记（不影响现网行为）。
- [x] 扩展 `ApiAccessLogFilter`：支持记录 `/api/**`，并输出 `candidateApi=true/false`。
- [x] 新增配置 `app.api-audit.*`，可开关审计与匹配范围。

## 访问统计方法（用于删接口前决策）

部署后观察日志：

```bash
journalctl -u travelflow -f
```

筛选候选接口命中：

```bash
journalctl -u travelflow --since "7 days ago" | grep "candidateApi=true"
```

建议观察窗口：

- 最少 `7` 天
- 最稳 `14` 天

## 删除策略（建议）

1. 先观察 `7-14` 天访问日志。
2. 命中为 `0` 的候选接口进入“可删除清单”。
3. 下一版本执行真正删除（Controller 方法 + DTO + Service + 文档）。

