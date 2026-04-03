# RAG + LangChain + Agent 升级说明

## 目标
本次改造按三步路线完成：
1. RAG 升级（混合检索、重排、元数据过滤、引用增强）
2. LangChain 编排接入（Prompt 模板层）
3. 单 Agent 能力接入（工具执行 + 轨迹输出）

## 我做了什么

### 1) RAG 升级
- 引入 `HybridRetrievalService`，将“向量检索 + 关键词检索(BM25风格评分) + 融合重排”放入统一检索链路。
- 在聊天请求里增加元数据过滤参数：
  - `sourceType`
  - `sourceRefContains`
- 扩展引用结构 `RagReferenceItem`，新增：
  - `sourceType`, `sourceRef`
  - `vectorScore`, `lexicalScore`, `rerankScore`
  - `score`（最终融合分数）
- RAG 参数新增配置：
  - `app.rag.recall-top-k`
  - `app.rag.lexical-pool-size`
  - `app.rag.vector-weight`
  - `app.rag.rerank-coverage-weight`
  - `app.rag.agent-max-steps`

### 2) LangChain 接入
- 新增 `RagLangChainComposer`，使用 `LangChain4j PromptTemplate` 来编排系统提示词和知识上下文。
- `RagChatService` 现在通过 `RagLangChainComposer` 组装消息，再调用现有 Bailian 客户端。
- 在 `demo-app/pom.xml` 增加 `langchain4j` 依赖，形成“检索层 + 编排层 + 模型调用层”分层结构。

### 3) Agent 接入
- 新增 `RagAgentService`，实现单 Agent 流程：
  1. 工具 `knowledge_search`（调用混合检索）
  2. 工具 `budget_estimator`（按问题文本做快速预算估算）
  3. 汇总工具输出 + 引用上下文，让模型生成最终答案
- 新增 Agent 接口：
  - `POST /api/v1/chat/agent/ask`
- Agent 返回包含工具执行轨迹（可通过 `includeTrace=false` 关闭）。

### 4) 前端接入
- 助手页面新增 `Agent 模式` 开关，用户可在 RAG / Agent 两种模式间切换。
- 助手页面新增检索过滤输入：
  - `知识来源类型（sourceType）`
  - `来源标识包含（sourceRefContains）`
- 关键词生成入口已切到新接口：
  - RAG: `POST /api/v1/chat/ask`
  - Agent: `POST /api/v1/chat/agent/ask`
- 页面新增两个结果区块：
  - `检索引用`（展示来源与分数）
  - `Agent 工具轨迹`（展示每一步工具输入/输出摘要）
- 当本轮使用 RAG/Agent 生成后，后续追问会自动复用该会话的 `sessionId`。

## 新增/修改文件与用途

### 核心新增
- `demo-app/src/main/java/com/example/demo/rag/service/HybridRetrievalService.java`
  - 混合召回、BM25风格评分、分数融合、覆盖率重排。
- `demo-app/src/main/java/com/example/demo/rag/model/HybridSearchHit.java`
  - 混合检索中间结果模型。
- `demo-app/src/main/java/com/example/demo/rag/langchain/RagLangChainComposer.java`
  - LangChain PromptTemplate 编排消息。
- `demo-app/src/main/java/com/example/demo/rag/service/RagAgentService.java`
  - 单 Agent 流程与工具执行轨迹。
- `demo-app/src/main/java/com/example/demo/rag/controller/AgentChatController.java`
  - Agent HTTP 接口。
- `demo-app/src/main/java/com/example/demo/rag/dto/AgentChatRequest.java`
- `demo-app/src/main/java/com/example/demo/rag/dto/AgentChatResponse.java`
- `demo-app/src/main/java/com/example/demo/rag/dto/AgentToolTrace.java`
  - Agent 请求/响应 DTO。

### 现有文件改造
- `demo-app/src/main/java/com/example/demo/rag/service/RagChatService.java`
  - 接入混合检索 + LangChain 编排。
- `demo-app/src/main/java/com/example/demo/rag/service/KnowledgeBaseService.java`
  - 支持混合检索引用映射。
- `demo-app/src/main/java/com/example/demo/rag/dto/ChatRequest.java`
  - 增加检索过滤字段。
- `demo-app/src/main/java/com/example/demo/rag/dto/RagReferenceItem.java`
  - 增加引用与评分细节字段。
- `demo-app/src/main/java/com/example/demo/rag/config/RagPipelineProperties.java`
  - 新增混合检索与 Agent 参数。
- `demo-app/src/main/resources/application.yml`
  - 新增 `app.rag.*` 配置项。
- `demo-app/src/main/java/com/example/demo/rag/repo/KnowledgeDocumentRepository.java`
  - 增加按状态查询。
- `demo-app/src/main/java/com/example/demo/rag/repo/KnowledgeChunkRepository.java`
  - 增加按文档ID批量查分片。
- `demo-app/pom.xml`
  - 增加 LangChain4j 依赖。

### 评测新增
- `demo-app/eval/rag_eval_cases.json`
  - RAG 评测样例集合。
- `demo-app/eval/run-rag-eval.ps1`
  - 对 `/api/v1/chat/ask` 批量打分并生成 Markdown 报告。

### 前端新增/修改
- `frontend/src/composables/useTravelApp.ts`
  - 接入新接口调用、会话复用、引用/轨迹状态管理。
- `frontend/src/pages/AssistantPage.vue`
  - 新增 Agent 开关、过滤字段输入、引用与轨迹展示卡片。
- `frontend/src/style.css`
  - 新增引用列表与轨迹列表样式。

## 接口使用示例

### 1) 升级后的 RAG 聊天
`POST /api/v1/chat/ask`

```json
{
  "sessionId": null,
  "question": "帮我规划杭州3天行程，预算5000",
  "topK": 5,
  "sourceType": "guide",
  "sourceRefContains": "hangzhou"
}
```

### 2) Agent 聊天
`POST /api/v1/chat/agent/ask`

```json
{
  "sessionId": null,
  "question": "两个人去成都4天，预算6000，给我可执行行程",
  "topK": 5,
  "sourceType": null,
  "sourceRefContains": null,
  "includeTrace": true
}
```

## 评测脚本使用
在 `demo-app` 目录下执行：

```powershell
.\eval\run-rag-eval.ps1 -Token "<你的JWT>" -BaseUrl "http://localhost:8080"
```

默认输出：`.\eval\rag_eval_report.md`

## 设计说明（简版）
- 先做检索质量提升，再做编排，再做 Agent，是为了保证每层都可回滚。
- Agent 先走“单 Agent + 明确工具”路径，避免一开始多 Agent 带来的不稳定性。
- LangChain 层只负责“编排”，模型和向量库仍复用你现有基础设施，迁移成本低。
