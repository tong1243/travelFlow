package com.example.demo.rag.service.agent;

import com.example.demo.rag.dto.RagReferenceItem;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Order(12)
/**
 * WebSearchFallbackTool 类。
 * 作为智能体工具节点参与编排流程，负责触发判断、执行与轨迹写入。
 * 通过与 AgentToolRuntime 协作，将中间结果沉淀为后续推理可消费的数据。
 */
public class WebSearchFallbackTool implements AgentTool {

    private final WebSearchFallbackService webSearchFallbackService;

    /**
     * 构造并初始化 WebSearchFallbackTool 对象。
     * 通过依赖注入完成必需组件装配，确保实例创建后即可参与完整流程。
     * 初始化阶段不会触发业务副作用，仅完成运行准备。
     * @param webSearchFallbackService 输入参数 webSearchFallbackService。
     */
    public WebSearchFallbackTool(WebSearchFallbackService webSearchFallbackService) {
        this.webSearchFallbackService = webSearchFallbackService;
    }

    @Override
    /**
     * 返回工具在编排链路中的名称。
     * 该名称会写入轨迹与日志，用于排障、评估和前端可视化展示。
     * 建议保持语义稳定，避免影响已有监控和调用方解析。
     * @return 工具名称字符串，用于链路追踪与前端展示。
     */
    public String toolName() {
        return "联网搜索兜底";
    }

    @Override
    /**
     * 判断当前工具是否应在本轮触发。
     * 根据上下文状态、用户意图和开关配置做轻量判定。
     * 返回 `false` 时仅跳过执行，不影响后续工具继续运行。
     * @param context 工具执行上下文，包含运行时状态、请求参数和权限开关。
     * @return 判断结果：`true` 表示满足条件，`false` 表示不满足条件。
     */
    public boolean shouldRun(AgentToolExecutionContext context) {
        return true;
    }

    @Override
    /**
     * 执行工具主逻辑并回写运行时状态。
     * 通常会调用内部服务完成查询或计算，再把摘要写入运行时对象。
     * 同时补充工具轨迹，保证每一步处理都可追踪、可解释。
     * @param context 工具执行上下文，包含运行时状态、请求参数和权限开关。
     */
    public void execute(AgentToolExecutionContext context) {
        AgentToolRuntime runtime = context.runtime();
        List<RagReferenceItem> localRefs = runtime.getReferences() == null ? List.of() : runtime.getReferences();
        List<String> queries = buildSearchQueries(runtime.getQuestion());

        List<RagReferenceItem> webRefs = new ArrayList<>();
        List<String> querySummaries = new ArrayList<>();
        for (String query : queries) {
            WebSearchFallbackService.WebSearchResult result = webSearchFallbackService.search(query);
            webRefs.addAll(result.references());
            querySummaries.add("[" + query + "] " + result.summary());
        }

        List<RagReferenceItem> dedupedWebRefs = deduplicateReferences(webRefs);
        if (!dedupedWebRefs.isEmpty()) {
            List<RagReferenceItem> merged = new ArrayList<>(dedupedWebRefs);
            merged.addAll(localRefs);
            int keep = Math.max(runtime.getTopK(), Math.min(12, merged.size()));
            if (merged.size() > keep) {
                merged = new ArrayList<>(merged.subList(0, keep));
            }
            runtime.setReferences(merged);
        }

        double maxRerank = localRefs.stream()
                .mapToDouble(item -> item == null ? 0.0 : item.rerankScore())
                .max()
                .orElse(0.0);
        runtime.addTrace(
                toolName(),
                "本地引用数=" + localRefs.size()
                        + "，本地最高重排分=" + String.format(Locale.ROOT, "%.4f", maxRerank)
                        + "，联网查询数=" + queries.size(),
                "联网补充去重后共 " + dedupedWebRefs.size() + " 条；"
                        + String.join("；", querySummaries)
        );
    }

    private List<String> buildSearchQueries(String question) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        String destinationCity = resolveCity(question, "destination_city", "destinationcity", "目的地", "目的城市");
        String departureCity = resolveCity(question, "departure_city", "departurecity", "出发地", "出发城市");
        String startDate = TravelGeoUtils.extractStructuredValue(question, "travel_start_date", "start_date");
        if (!startDate.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            startDate = "";
        }

        if (!destinationCity.isBlank()) {
            queries.add(destinationCity + " 美食 推荐 店名 人均 排队 预约");
            queries.add(destinationCity + " 地铁 线路 换乘 方案 景点");
            queries.add(destinationCity + " 景点 预约 放票 时间 游玩路线 拍照机位");
            queries.add(destinationCity + " 天气预警 实时人流 出行提示");
        }

        if (!departureCity.isBlank() && !destinationCity.isBlank()) {
            String transport = departureCity + " 到 " + destinationCity + " 机票 高铁 实时价格";
            if (!startDate.isBlank()) {
                transport = transport + " " + startDate;
            }
            queries.add(transport);
        }

        if (queries.isEmpty()) {
            String normalized = normalizeQuestionForSearch(question);
            if (!normalized.isBlank()) {
                queries.add(normalized);
            }
        }

        List<String> list = new ArrayList<>(queries);
        if (list.size() > 5) {
            return new ArrayList<>(list.subList(0, 5));
        }
        return list;
    }

    private String resolveCity(String question, String... labels) {
        String structured = TravelGeoUtils.extractStructuredValue(question, labels);
        if (structured != null && !structured.isBlank()) {
            return TravelGeoUtils.normalizeCityToken(structured);
        }
        String labeled = TravelGeoUtils.extractLabeledValue(question, labels);
        return TravelGeoUtils.normalizeCityToken(labeled);
    }

    private String normalizeQuestionForSearch(String question) {
        if (question == null || question.isBlank()) {
            return "";
        }
        String[] lines = question.split("\\r?\\n");
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            int sep = trimmed.indexOf(':');
            if (sep <= 0) {
                sep = trimmed.indexOf('=');
            }
            if (sep > 0) {
                String key = trimmed.substring(0, sep).trim();
                if (key.matches("^[a-zA-Z_]{3,}$")) {
                    continue;
                }
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(trimmed);
        }
        String normalized = builder.toString().replaceAll("\\s{2,}", " ").trim();
        if (normalized.length() > 80) {
            normalized = normalized.substring(0, 80);
        }
        return normalized;
    }

    private List<RagReferenceItem> deduplicateReferences(List<RagReferenceItem> references) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }
        Map<String, RagReferenceItem> dedup = new LinkedHashMap<>();
        for (RagReferenceItem item : references) {
            if (item == null) {
                continue;
            }
            String key = (fallback(item.sourceRef(), "") + "|"
                    + fallback(item.documentTitle(), "") + "|"
                    + fallback(item.snippet(), ""))
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("\\s+", " ")
                    .trim();
            if (key.isBlank()) {
                continue;
            }
            dedup.putIfAbsent(key, item);
        }
        return new ArrayList<>(dedup.values());
    }

    private String fallback(String text, String defaultValue) {
        return text == null || text.isBlank() ? defaultValue : text;
    }
}
