package com.example.demo.rag.service.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class TravelGeoUtils {

    private static final Map<String, String> CITY_ALIAS_MAP = buildCityAliasMap();
    private static final Set<String> NOISE_WORDS = Set.of(
            "天气", "酒店", "住宿", "住", "机票", "航班", "车票", "高铁", "火车",
            "旅行", "旅游", "行程", "方案", "目的地", "需求", "出发地", "出行",
            "入住", "离店", "预算", "人数", "同行", "风格", "检索语句", "结构化", "计划"
    );

    private TravelGeoUtils() {
    }

    static String extractLabeledValue(String text, String... labels) {
        if (text == null || text.isBlank() || labels == null) {
            return "";
        }
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            for (String label : labels) {
                if (label == null || label.isBlank()) {
                    continue;
                }
                int idx = trimmed.indexOf(label + "：");
                int alt = trimmed.indexOf(label + ":");
                int eq = trimmed.indexOf(label + "=");
                int start = idx >= 0 ? idx + label.length() + 1
                        : (alt >= 0 ? alt + label.length() + 1
                        : (eq >= 0 ? eq + label.length() + 1 : -1));
                if (start >= 0 && start < trimmed.length()) {
                    return trimmed.substring(start).trim();
                }
            }
        }
        return "";
    }

    static String extractStructuredValue(String text, String... keys) {
        if (text == null || text.isBlank() || keys == null) {
            return "";
        }
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            int colon = trimmed.indexOf(':');
            int eq = trimmed.indexOf('=');
            int sep = -1;
            if (colon > 0) {
                sep = colon;
            }
            if (eq > 0 && (sep < 0 || eq < sep)) {
                sep = eq;
            }
            if (sep <= 0) {
                continue;
            }
            String key = trimmed.substring(0, sep).trim().toLowerCase(Locale.ROOT);
            String value = trimmed.substring(sep + 1).trim();
            for (String candidate : keys) {
                if (candidate == null || candidate.isBlank()) {
                    continue;
                }
                if (key.equals(candidate.toLowerCase(Locale.ROOT))) {
                    return value;
                }
            }
        }
        return "";
    }

    static String normalizeCityToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String cleaned = raw.trim()
                .replace('\u00A0', ' ')
                .replaceAll("[\\r\\n\\t]", " ")
                .replace("（", " ")
                .replace("）", " ")
                .replace("(", " ")
                .replace(")", " ")
                .replace("，", " ")
                .replace(",", " ")
                .replace("。", " ")
                .replace("；", " ")
                .replace(";", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (cleaned.isBlank()) {
            return "";
        }

        String lowered = cleaned.toLowerCase(Locale.ROOT);
        if (CITY_ALIAS_MAP.containsKey(lowered)) {
            return CITY_ALIAS_MAP.get(lowered);
        }

        for (Map.Entry<String, String> entry : CITY_ALIAS_MAP.entrySet()) {
            if (lowered.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        String[] parts = cleaned.split("\\s+");
        String first = parts.length == 0 ? cleaned : parts[0];
        String candidate = first;
        for (String word : NOISE_WORDS) {
            candidate = candidate.replace(word, "");
        }
        candidate = candidate
                .replaceAll("(特别行政区|壮族自治区|回族自治区|维吾尔自治区|自治区|自治州|地区|盟)$", "")
                .replaceAll("(省|市|县|区)$", "")
                .trim();
        if (candidate.isBlank()) {
            candidate = first.trim();
        }

        String mapped = CITY_ALIAS_MAP.get(candidate.toLowerCase(Locale.ROOT));
        return mapped == null || mapped.isBlank() ? candidate : mapped;
    }

    static List<String> expandCityCandidates(String raw) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String normalized = normalizeCityToken(raw);
        if (!normalized.isBlank()) {
            candidates.add(normalized);
            candidates.add(normalized.toLowerCase(Locale.ROOT));
            if (normalized.chars().anyMatch(ch -> ch > 127)) {
                candidates.add(normalized + "市");
            }
        }
        if (raw != null && !raw.isBlank()) {
            candidates.add(raw.trim());
            candidates.add(raw.trim().toLowerCase(Locale.ROOT));
        }
        return new ArrayList<>(candidates);
    }

    private static Map<String, String> buildCityAliasMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("魔都", "上海");
        map.put("帝都", "北京");
        map.put("羊城", "广州");
        map.put("鹏城", "深圳");
        map.put("蓉城", "成都");
        map.put("山城", "重庆");
        map.put("金陵", "南京");
        map.put("钱塘", "杭州");
        map.put("姑苏", "苏州");
        map.put("长安", "西安");
        map.put("冰城", "哈尔滨");
        map.put("春城", "昆明");
        map.put("榕城", "福州");

        map.put("外滩", "上海");
        map.put("迪士尼", "上海");
        map.put("故宫", "北京");
        map.put("天安门", "北京");
        map.put("兵马俑", "西安");
        map.put("西湖", "杭州");
        map.put("洪崖洞", "重庆");
        map.put("宽窄巷子", "成都");
        map.put("橘子洲", "长沙");
        map.put("鼓浪屿", "厦门");
        map.put("九寨沟", "成都");
        map.put("张家界国家森林公园", "张家界");
        map.put("黄山风景区", "黄山");

        map.put("beijing", "北京");
        map.put("shanghai", "上海");
        map.put("guangzhou", "广州");
        map.put("shenzhen", "深圳");
        map.put("hangzhou", "杭州");
        map.put("nanjing", "南京");
        map.put("chengdu", "成都");
        map.put("chongqing", "重庆");
        map.put("xian", "西安");
        map.put("xi'an", "西安");
        map.put("wuhan", "武汉");
        map.put("xiamen", "厦门");
        map.put("qingdao", "青岛");
        map.put("kunming", "昆明");
        map.put("changsha", "长沙");
        map.put("zhengzhou", "郑州");
        map.put("sanya", "三亚");
        map.put("hong kong", "香港");
        map.put("hongkong", "香港");
        map.put("macau", "澳门");
        map.put("macao", "澳门");
        return map;
    }
}
