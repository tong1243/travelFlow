package com.example.demo.rag.service.agent;

import com.example.demo.rag.config.TrainLookupProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TrainLookupService {

    private static final Pattern FROM_TO_PATTERN = Pattern.compile("(?:从|from)\\s*([\\p{IsHan}A-Za-z]{2,20})\\s*(?:到|to|->|→)\\s*([\\p{IsHan}A-Za-z]{2,20})", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})");
    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile("(\\d{1,2})月(\\d{1,2})日");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Map<String, String> CITY_TO_STATION = buildStationCodeMap();

    private final TrainLookupProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public TrainLookupService(TrainLookupProperties properties,
                              RestTemplateBuilder restTemplateBuilder,
                              ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())))
                .readTimeout(Duration.ofSeconds(Math.max(1, properties.getReadTimeoutSeconds())))
                .build();
    }

    public TrainLookupResult lookupTrains(String question) {
        if (!properties.isEnabled()) {
            return TrainLookupResult.failed("车票查询已在系统配置中关闭。");
        }

        TrainRoute route = extractRoute(question);
        if (route == null) {
            return TrainLookupResult.failed("已识别车票查询意图，但未解析到出发地和目的地。");
        }

        String fromCode = toStationCode(route.departure());
        String toCode = toStationCode(route.arrival());
        if (fromCode == null || toCode == null) {
            return TrainLookupResult.failed("已识别路线，但未匹配到12306车站代码："
                    + route.departure() + " -> " + route.arrival()
                    + "。建议改成更标准的站名，如“襄阳东站/武汉站”。");
        }

        LocalDate date = extractDate(question);
        String travelDate = date.format(DATE_FORMATTER);

        String lastFailure = "车票服务调用失败，请稍后重试。";
        for (String url : buildCandidateUrls(travelDate, fromCode, toCode)) {
            try {
                String cookie = bootstrapCookie();
                ResponseEntity<String> entity = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        new HttpEntity<>(buildHeaders(cookie)),
                        String.class
                );
                String body = entity.getBody();
                if (body == null || body.isBlank()) {
                    lastFailure = "车票服务未返回有效数据，请稍后重试。";
                    continue;
                }
                if (looksLikeHtml(body)) {
                    lastFailure = "12306 返回了拦截页面（可能触发反爬或频控），请稍后重试。";
                    continue;
                }

                TrainApiResponse response = objectMapper.readValue(body, TrainApiResponse.class);
                if (response == null || response.data == null || response.data.result == null || response.data.result.isEmpty()) {
                    lastFailure = "未查询到符合条件的车次，请调整日期或线路后重试。";
                    continue;
                }

                Map<String, String> stationNameMap = response.data.map == null ? Map.of() : response.data.map;
                List<TrainLine> lines = parseTrainLines(response.data.result, stationNameMap, Math.max(1, properties.getLimit()));
                if (lines.isEmpty()) {
                    lastFailure = "车票服务返回了数据，但未能解析出可展示车次。";
                    continue;
                }

                StringBuilder builder = new StringBuilder();
                builder.append("车票查询：")
                        .append(route.departure()).append("(").append(fromCode).append(")")
                        .append(" -> ")
                        .append(route.arrival()).append("(").append(toCode).append(")")
                        .append("，出行日期 ").append(travelDate)
                        .append("，优选结果 ").append(lines.size()).append(" 班（仅展示最推荐车次，已优先过滤凌晨时段）。")
                        .append("\n12306 查询链接：")
                        .append(buildOfficialSearchLink(travelDate, fromCode, toCode));

                int index = 1;
                for (TrainLine line : lines) {
                    String bookingLink = buildTrainBookingLink(travelDate, fromCode, toCode, line.trainCode());
                    builder.append('\n')
                            .append(index++).append(") ")
                            .append(line.trainCode())
                            .append(" | ")
                            .append(line.fromName()).append(' ').append(line.startTime())
                            .append(" -> ")
                            .append(line.toName()).append(' ').append(line.arriveTime())
                            .append(" | 历时 ").append(line.duration())
                            .append(" | 商务座 ").append(line.swz())
                            .append(" | 一等座 ").append(line.zy())
                            .append(" | 二等座 ").append(line.ze())
                            .append(" | 硬卧 ").append(line.yw())
                            .append(" | 硬座 ").append(line.yz())
                            .append(" | 无座 ").append(line.wz())
                            .append(" | [去预订](").append(bookingLink).append(")");
                }
                return TrainLookupResult.success(builder.toString());
            } catch (Exception ex) {
                lastFailure = "车票服务调用失败，请稍后重试。";
            }
        }

        return TrainLookupResult.failed(lastFailure);
    }

    private HttpHeaders buildHeaders(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36");
        headers.set(HttpHeaders.ACCEPT, "application/json,text/plain,*/*");
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9");
        headers.set(HttpHeaders.REFERER, "https://kyfw.12306.cn/otn/leftTicket/init");
        headers.set(HttpHeaders.ORIGIN, "https://kyfw.12306.cn");
        headers.set("X-Requested-With", "XMLHttpRequest");
        headers.set(HttpHeaders.CACHE_CONTROL, "no-cache");
        headers.set(HttpHeaders.PRAGMA, "no-cache");
        if (cookie != null && !cookie.isBlank()) {
            headers.set(HttpHeaders.COOKIE, cookie);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String bootstrapCookie() {
        if (!properties.isCookieBootstrapEnabled()) {
            return "";
        }
        String bootstrapUrl = properties.getBootstrapUrl();
        if (bootstrapUrl == null || bootstrapUrl.isBlank()) {
            return "";
        }
        try {
            ResponseEntity<String> entity = restTemplate.exchange(
                    bootstrapUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders("")),
                    String.class
            );
            List<String> cookies = entity.getHeaders().get(HttpHeaders.SET_COOKIE);
            if (cookies == null || cookies.isEmpty()) {
                return "";
            }
            List<String> merged = new ArrayList<>();
            for (String item : cookies) {
                if (item == null || item.isBlank()) {
                    continue;
                }
                int split = item.indexOf(';');
                merged.add(split > 0 ? item.substring(0, split) : item);
            }
            return String.join("; ", merged);
        } catch (Exception ignored) {
            return "";
        }
    }

    private List<String> buildCandidateUrls(String travelDate, String fromCode, String toCode) {
        String configured = properties.getBaseUrl();
        if (configured == null || configured.isBlank()) {
            configured = "https://kyfw.12306.cn/otn/leftTicket/query";
        }

        List<String> baseUrls = new ArrayList<>();
        baseUrls.add(configured.trim());
        if (properties.getAlternativeBaseUrls() != null) {
            for (String alt : properties.getAlternativeBaseUrls()) {
                if (alt != null && !alt.isBlank()) {
                    baseUrls.add(alt.trim());
                }
            }
        }
        if (configured.contains("/query")) {
            baseUrls.add(configured.replace("/query", "/queryG"));
            baseUrls.add(configured.replace("/query", "/queryO"));
        } else {
            baseUrls.add("https://kyfw.12306.cn/otn/leftTicket/query");
            baseUrls.add("https://kyfw.12306.cn/otn/leftTicket/queryG");
            baseUrls.add("https://kyfw.12306.cn/otn/leftTicket/queryO");
        }

        Set<String> dedup = new LinkedHashSet<>();
        for (String base : baseUrls) {
            String url = UriComponentsBuilder.fromUriString(base)
                    .queryParam("leftTicketDTO.train_date", travelDate)
                    .queryParam("leftTicketDTO.from_station", fromCode)
                    .queryParam("leftTicketDTO.to_station", toCode)
                    .queryParam("purpose_codes", "ADULT")
                    .toUriString();
            dedup.add(url);
        }
        return new ArrayList<>(dedup);
    }

    private String buildOfficialSearchLink(String travelDate, String fromCode, String toCode) {
        return UriComponentsBuilder.fromUriString("https://kyfw.12306.cn/otn/leftTicket/init")
                .queryParam("linktypeid", "dc")
                .queryParam("fs", fromCode)
                .queryParam("ts", toCode)
                .queryParam("date", travelDate)
                .toUriString();
    }

    private String buildTrainBookingLink(String travelDate, String fromCode, String toCode, String trainCode) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString("https://kyfw.12306.cn/otn/leftTicket/init")
                .queryParam("linktypeid", "dc")
                .queryParam("fs", fromCode)
                .queryParam("ts", toCode)
                .queryParam("date", travelDate);
        if (trainCode != null && !trainCode.isBlank()) {
            // 12306 会忽略未知参数，但这里保留车次标识，确保每条候选链接都可区分。
            builder.queryParam("trainCode", trainCode.trim().toUpperCase(Locale.ROOT));
        }
        return builder.toUriString();
    }

    private boolean looksLikeHtml(String body) {
        String text = body.trim().toLowerCase(Locale.ROOT);
        return text.startsWith("<!doctype")
                || text.startsWith("<html")
                || text.contains("验证")
                || text.contains("访问过于频繁")
                || text.contains("铁路客户服务中心");
    }

    private List<TrainLine> parseTrainLines(List<String> rawLines, Map<String, String> stationNameMap, int limit) {
        List<TrainLine> lines = new ArrayList<>();
        for (String raw : rawLines) {
            TrainLine line = TrainLine.parse(raw, stationNameMap);
            if (line != null) {
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            return List.of();
        }
        return recommendTrainLines(lines, limit);
    }

    private List<TrainLine> recommendTrainLines(List<TrainLine> allLines, int configuredLimit) {
        if (allLines == null || allLines.isEmpty()) {
            return List.of();
        }
        int upper = Math.max(3, Math.min(5, configuredLimit <= 0 ? 5 : configuredLimit));
        List<TrainLine> withSeat = allLines.stream().filter(TrainLine::hasUsableSeat).toList();
        List<TrainLine> seatPool = withSeat.isEmpty() ? allLines : withSeat;

        List<TrainLine> dayPool = seatPool.stream().filter(TrainLine::isPreferredDepartureTime).toList();
        List<TrainLine> basePool = dayPool.size() >= 3 ? dayPool : seatPool;

        List<TrainLine> sorted = basePool.stream()
                .sorted(Comparator
                        .comparingDouble(TrainLine::recommendationScore)
                        .thenComparingInt(TrainLine::departureMinutes)
                        .thenComparingInt(TrainLine::durationMinutes))
                .toList();

        if (sorted.size() <= upper) {
            return sorted;
        }
        return new ArrayList<>(sorted.subList(0, upper));
    }

    private TrainRoute extractRoute(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }

        String fromStructured = cleanupName(extractStructuredValue(question, "departure_city", "departurecity", "from_city"));
        String toStructured = cleanupName(extractStructuredValue(question, "destination_city", "destinationcity", "to_city", "arrival_city"));
        if (!fromStructured.isBlank() && !toStructured.isBlank()) {
            return new TrainRoute(fromStructured, toStructured);
        }

        TrainRoute byPattern = matchRoute(question);
        if (byPattern != null) {
            return byPattern;
        }

        String fromLabel = cleanupName(extractLabeledValue(question, "出发地"));
        String toLabel = cleanupName(extractLabeledValue(question, "目的地", "目的地或需求"));
        if (!fromLabel.isBlank() && !toLabel.isBlank()) {
            return new TrainRoute(fromLabel, toLabel);
        }

        return null;
    }

    private TrainRoute matchRoute(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = FROM_TO_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String from = cleanupName(matcher.group(1));
        String to = cleanupName(matcher.group(2));
        if (from.isBlank() || to.isBlank()) {
            return null;
        }
        return new TrainRoute(from, to);
    }

    private LocalDate extractDate(String question) {
        LocalDate fromStructured = parseDateFromText(extractStructuredValue(question,
                "travel_start_date", "travelstartdate", "start_date", "departure_date"));
        if (fromStructured != null) {
            return fromStructured;
        }

        LocalDate fromQuestion = parseDateFromText(question);
        if (fromQuestion != null) {
            return fromQuestion;
        }

        return LocalDate.now();
    }

    private LocalDate parseDateFromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        Matcher fullMatcher = DATE_PATTERN.matcher(text);
        if (fullMatcher.find()) {
            try {
                int y = Integer.parseInt(fullMatcher.group(1));
                int m = Integer.parseInt(fullMatcher.group(2));
                int d = Integer.parseInt(fullMatcher.group(3));
                return LocalDate.of(y, m, d);
            } catch (Exception ignored) {
                // continue
            }
        }

        Matcher mdMatcher = MONTH_DAY_PATTERN.matcher(text);
        if (mdMatcher.find()) {
            try {
                int month = Integer.parseInt(mdMatcher.group(1));
                int day = Integer.parseInt(mdMatcher.group(2));
                LocalDate now = LocalDate.now();
                LocalDate candidate = LocalDate.of(now.getYear(), month, day);
                if (candidate.isBefore(now)) {
                    candidate = candidate.plusYears(1);
                }
                return candidate;
            } catch (Exception ignored) {
                // continue
            }
        }

        return null;
    }

    private String toStationCode(String cityOrStation) {
        if (cityOrStation == null || cityOrStation.isBlank()) {
            return null;
        }

        List<String> candidates = buildNameCandidates(cityOrStation);
        for (String candidate : candidates) {
            String mapped = CITY_TO_STATION.get(candidate.toLowerCase(Locale.ROOT));
            if (mapped != null && !mapped.isBlank()) {
                return mapped;
            }
        }

        for (String candidate : candidates) {
            for (Map.Entry<String, String> entry : CITY_TO_STATION.entrySet()) {
                if (candidate.toLowerCase(Locale.ROOT).contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }

        String raw = cityOrStation.trim();
        if (raw.matches("^[A-Za-z]{3}$")) {
            return raw.toUpperCase(Locale.ROOT);
        }

        return null;
    }

    private List<String> buildNameCandidates(String text) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        String cleaned = cleanupName(text);
        if (cleaned.isBlank()) {
            return List.of();
        }

        values.add(cleaned);
        values.add(cleaned.replace("火车站", "").replace("高铁站", "").replace("站", ""));

        String noProvinceCity = cleaned.replace("省", "").replace("市", "");
        values.add(noProvinceCity);
        values.add(noProvinceCity.replace("站", ""));

        if (noProvinceCity.endsWith("东") || noProvinceCity.endsWith("西") || noProvinceCity.endsWith("南") || noProvinceCity.endsWith("北")) {
            values.add(noProvinceCity.substring(0, noProvinceCity.length() - 1));
        }

        List<String> out = new ArrayList<>();
        for (String item : values) {
            if (item == null) {
                continue;
            }
            String normalized = item.trim().replaceAll("\\s+", "");
            if (!normalized.isBlank()) {
                out.add(normalized);
            }
        }
        return out;
    }

    private String cleanupName(String text) {
        if (text == null) {
            return "";
        }
        return text.trim()
                .replace('（', ' ')
                .replace('）', ' ')
                .replace('(', ' ')
                .replace(')', ' ')
                .replace("出发地", "")
                .replace("目的地", "")
                .replace("路线", "")
                .replace("行程", "")
                .replace("旅行", "")
                .replace("旅游", "")
                .replace("方案", "")
                .replace(",", " ")
                .replace("，", " ")
                .replace("。", " ")
                .replace(";", " ")
                .replace("；", " ")
                .replaceAll("\\s+", "")
                .trim();
    }

    private String extractLabeledValue(String text, String... labels) {
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
                if (idx < 0) {
                    idx = trimmed.indexOf(label + ":");
                }
                if (idx >= 0) {
                    String value = trimmed.substring(idx + label.length() + 1).trim();
                    if (!value.isBlank()) {
                        return value;
                    }
                }
            }
        }
        return "";
    }

    private String extractStructuredValue(String text, String... keys) {
        if (text == null || text.isBlank() || keys == null) {
            return "";
        }
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            int sep = trimmed.indexOf(':');
            if (sep < 0) {
                sep = trimmed.indexOf('=');
            }
            if (sep <= 0 || sep >= trimmed.length() - 1) {
                continue;
            }
            String key = trimmed.substring(0, sep).trim();
            String value = trimmed.substring(sep + 1).trim();
            for (String candidate : keys) {
                if (candidate != null && key.equalsIgnoreCase(candidate)) {
                    return value;
                }
            }
        }
        return "";
    }

    private static Map<String, String> buildStationCodeMap() {
        Map<String, String> map = new LinkedHashMap<>();

        // 华北/华东常用
        map.put("北京", "BJP");
        map.put("北京西", "BXP");
        map.put("北京南", "VNP");
        map.put("上海", "SHH");
        map.put("上海虹桥", "AOH");
        map.put("杭州", "HZH");
        map.put("南京", "NJH");
        map.put("苏州", "SZH");
        map.put("无锡", "WXH");
        map.put("合肥", "HFH");

        // 华中/华南
        map.put("武汉", "WHN");
        map.put("武汉站", "WHN");
        map.put("武汉东", "WHN");
        map.put("襄阳", "XFN");
        map.put("襄阳站", "XFN");
        map.put("襄阳东", "XFN");
        map.put("长沙", "CSQ");
        map.put("郑州", "ZZF");
        map.put("广州", "GZQ");
        map.put("广州南", "IZQ");
        map.put("深圳", "SZQ");
        map.put("深圳北", "IOQ");
        map.put("厦门", "XMS");
        map.put("福州", "FZS");

        // 西南/西北
        map.put("成都", "CDW");
        map.put("重庆", "CQW");
        map.put("西安", "XAY");
        map.put("昆明", "KMM");
        map.put("贵阳", "GIW");
        map.put("南宁", "NNZ");

        // 其他常用
        map.put("天津", "TJP");
        map.put("青岛", "QDK");
        map.put("济南", "JNK");
        map.put("沈阳", "SYT");
        map.put("大连", "DLT");
        map.put("哈尔滨", "HBB");

        // 英文别名
        map.put("beijing", "BJP");
        map.put("shanghai", "SHH");
        map.put("hangzhou", "HZH");
        map.put("nanjing", "NJH");
        map.put("wuhan", "WHN");
        map.put("xiangyang", "XFN");
        map.put("guangzhou", "GZQ");
        map.put("shenzhen", "SZQ");
        map.put("chengdu", "CDW");
        map.put("chongqing", "CQW");
        map.put("xian", "XAY");

        return map;
    }

    private static String normalizeSeat(String[] fields, int index) {
        String value = valueAt(fields, index);
        if (value.isBlank() || "--".equals(value) || "无".equals(value) || "null".equalsIgnoreCase(value)) {
            return "无";
        }
        if (value.matches("^\\d+$")) {
            return value + "张";
        }
        return value;
    }

    private static String valueAt(String[] fields, int index) {
        if (fields == null || index < 0 || index >= fields.length || fields[index] == null) {
            return "";
        }
        return fields[index].trim();
    }

    public record TrainLookupResult(boolean success, String summary) {
        public static TrainLookupResult success(String summary) {
            return new TrainLookupResult(true, summary == null ? "" : summary);
        }

        public static TrainLookupResult failed(String summary) {
            return new TrainLookupResult(false, summary == null ? "" : summary);
        }
    }

    private record TrainRoute(String departure, String arrival) {
    }

    private record TrainLine(String trainCode,
                             String fromName,
                             String toName,
                             String startTime,
                             String arriveTime,
                             String duration,
                             String swz,
                             String zy,
                             String ze,
                             String yw,
                             String yz,
                             String wz) {
        int departureMinutes() {
            return parseTimeMinutes(startTime);
        }

        int arrivalMinutes() {
            return parseTimeMinutes(arriveTime);
        }

        int durationMinutes() {
            if (duration == null || duration.isBlank()) {
                return 24 * 60;
            }
            Matcher matcher = Pattern.compile("(\\d{1,2}):(\\d{1,2})").matcher(duration.trim());
            if (!matcher.find()) {
                return 24 * 60;
            }
            try {
                int h = Integer.parseInt(matcher.group(1));
                int m = Integer.parseInt(matcher.group(2));
                if (h < 0 || m < 0 || m > 59) {
                    return 24 * 60;
                }
                return h * 60 + m;
            } catch (Exception ignored) {
                return 24 * 60;
            }
        }

        boolean isOvernight() {
            int dep = departureMinutes();
            int arr = arrivalMinutes();
            if (dep < 0 || arr < 0) {
                return false;
            }
            return arr < dep;
        }

        boolean isPreferredDepartureTime() {
            int dep = departureMinutes();
            if (dep < 0) {
                return true;
            }
            // 06:30 - 22:30 更适合一般旅行场景
            return dep >= 390 && dep <= 1350;
        }

        boolean hasUsableSeat() {
            return isSeatAvailable(swz) || isSeatAvailable(zy) || isSeatAvailable(ze)
                    || isSeatAvailable(yw) || isSeatAvailable(yz) || isSeatAvailable(wz);
        }

        double recommendationScore() {
            double score = 0.0;
            int dep = departureMinutes();
            if (dep >= 0) {
                if (dep < 390 || dep > 1350) {
                    score += 5.0;
                } else if (dep < 450 || dep > 1260) {
                    score += 1.2;
                }
            }
            int duration = durationMinutes();
            score += Math.max(0, duration - 120) / 120.0;

            if (isOvernight()) {
                score += 1.0;
            }
            if (!hasUsableSeat()) {
                score += 3.0;
            }

            String code = trainCode == null ? "" : trainCode.trim().toUpperCase(Locale.ROOT);
            if (code.startsWith("G")) {
                score -= 1.2;
            } else if (code.startsWith("D")) {
                score -= 0.8;
            } else if (code.startsWith("C")) {
                score -= 0.5;
            } else if (code.startsWith("K") || code.startsWith("T") || code.startsWith("Z")) {
                score += 0.5;
            }

            score -= Math.min(0.8, availableSeatKinds() * 0.2);
            return score;
        }

        private int availableSeatKinds() {
            int count = 0;
            if (isSeatAvailable(swz)) count++;
            if (isSeatAvailable(zy)) count++;
            if (isSeatAvailable(ze)) count++;
            if (isSeatAvailable(yw)) count++;
            if (isSeatAvailable(yz)) count++;
            if (isSeatAvailable(wz)) count++;
            return count;
        }

        private static int parseTimeMinutes(String value) {
            if (value == null || value.isBlank()) {
                return -1;
            }
            Matcher matcher = Pattern.compile("(\\d{1,2}):(\\d{1,2})").matcher(value.trim());
            if (!matcher.find()) {
                return -1;
            }
            try {
                int h = Integer.parseInt(matcher.group(1));
                int m = Integer.parseInt(matcher.group(2));
                if (h < 0 || h > 23 || m < 0 || m > 59) {
                    return -1;
                }
                return h * 60 + m;
            } catch (Exception ignored) {
                return -1;
            }
        }

        private static boolean isSeatAvailable(String seat) {
            if (seat == null) {
                return false;
            }
            String normalized = seat.trim();
            if (normalized.isBlank() || "无".equals(normalized) || "--".equals(normalized)) {
                return false;
            }
            if (normalized.contains("有")) {
                return true;
            }
            Matcher matcher = Pattern.compile("(\\d+)").matcher(normalized);
            if (!matcher.find()) {
                return false;
            }
            try {
                return Integer.parseInt(matcher.group(1)) > 0;
            } catch (Exception ignored) {
                return false;
            }
        }

        static TrainLine parse(String raw, Map<String, String> stationNameMap) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String[] fields = raw.split("\\|");
            if (fields.length < 33) {
                return null;
            }

            String trainCode = valueAt(fields, 3);
            String fromCode = valueAt(fields, 6);
            String toCode = valueAt(fields, 7);

            String fromName = stationNameMap.getOrDefault(fromCode, fromCode);
            String toName = stationNameMap.getOrDefault(toCode, toCode);

            return new TrainLine(
                    trainCode.isBlank() ? "车次未知" : trainCode,
                    fromName.isBlank() ? "起点站未知" : fromName,
                    toName.isBlank() ? "终点站未知" : toName,
                    valueAt(fields, 8),
                    valueAt(fields, 9),
                    valueAt(fields, 10),
                    normalizeSeat(fields, 32),
                    normalizeSeat(fields, 31),
                    normalizeSeat(fields, 30),
                    normalizeSeat(fields, 28),
                    normalizeSeat(fields, 29),
                    normalizeSeat(fields, 26)
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TrainApiResponse {
        public TrainData data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TrainData {
        public List<String> result;
        public Map<String, String> map;
    }
}
