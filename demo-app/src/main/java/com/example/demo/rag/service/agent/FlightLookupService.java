package com.example.demo.rag.service.agent;

import com.example.demo.rag.config.FlightLookupProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FlightLookupService {

    private static final Pattern FROM_TO_PATTERN = Pattern.compile(
            "(?:从|由)?\\s*([\\p{IsHan}A-Za-z]{2,20})\\s*(?:到|至|to|->|-)\\s*([\\p{IsHan}A-Za-z]{2,20})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})");
    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile("(\\d{1,2})月(\\d{1,2})日");
    private static final Pattern DEPART_PATTERN = Pattern.compile("出发日期\\s*(20\\d{2}-\\d{1,2}-\\d{1,2})");
    private static final Pattern IATA_TOKEN_PATTERN = Pattern.compile("\\b([A-Za-z]{3})\\b");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String DEFAULT_CTRIP_BASE_URL = "https://flights.ctrip.com/online/list/oneway-{dep}-{arr}";
    private static final Map<String, String> CITY_TO_IATA = buildCityIataMap();
    private static final Set<String> MAINLAND_CHINA_IATA = Set.of(
            "PEK", "PKX", "SHA", "PVG", "CAN", "SZX", "HGH", "NKG", "CTU", "TFU",
            "CKG", "XIY", "WUH", "XMN", "TAO", "SYX", "KMG", "TSN", "CGO", "CSX",
            "FOC", "HAK", "DLC", "NGB", "SHE", "URC", "HFE", "TNA", "JJN", "LHW"
    );

    private final FlightLookupProperties properties;
    private final RestTemplate restTemplate;

    public FlightLookupService(FlightLookupProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())))
                .readTimeout(Duration.ofSeconds(Math.max(1, properties.getReadTimeoutSeconds())))
                .build();
    }

    public FlightLookupResult lookupFlights(String question) {
        if (!properties.isEnabled()) {
            return FlightLookupResult.failed("机票查询已在系统配置中关闭。");
        }

        FlightRoute route = extractRoute(question);
        if (route == null) {
            return FlightLookupResult.failed("识别到机票意图，但未能解析出发地和目的地。");
        }

        String depIata = toIata(route.departure());
        String arrIata = toIata(route.arrival());
        LocalDate date = extractDate(question);

        if (isDomesticRoute(route, depIata, arrIata)) {
            String domesticProvider = fallback(properties.getDomesticProvider(), "ctrip");
            if (!"ctrip".equalsIgnoreCase(domesticProvider)) {
                return FlightLookupResult.failed("当前国内航线仅支持 ctrip 服务商，请检查 app.flight.domestic-provider 配置。");
            }
            return lookupDomesticViaCtrip(route, depIata, arrIata, date);
        }

        return lookupInternationalViaAviationstack(route, depIata, arrIata, date);
    }

    private FlightLookupResult lookupDomesticViaCtrip(FlightRoute route, String depIata, String arrIata, LocalDate date) {
        String depToken = depIata == null || depIata.isBlank() ? route.departure() : depIata;
        String arrToken = arrIata == null || arrIata.isBlank() ? route.arrival() : arrIata;
        String ctripUrl = buildCtripUrl(depToken, arrToken, date);
        String left = depIata == null || depIata.isBlank() ? route.departure() : route.departure() + "(" + depIata + ")";
        String right = arrIata == null || arrIata.isBlank() ? route.arrival() : route.arrival() + "(" + arrIata + ")";
        String summary = "机票查询：" + left + " -> " + right
                + "，出行日期 " + date.format(DATE_FORMATTER)
                + "，国内航线已切换携程。\n"
                + "携程查询链接：" + ctripUrl + "\n"
                + "说明：价格与余票请以携程页面实时展示为准。";
        return FlightLookupResult.success(summary);
    }

        private FlightLookupResult lookupInternationalViaAviationstack(FlightRoute route, String depIata, String arrIata, LocalDate date) {
        if (depIata == null || arrIata == null) {
            return FlightLookupResult.failed(withCtripFallback(
                    "已识别城市，但未匹配到机场 IATA 代码，请使用更标准的城市名称或直接输入三字码。",
                    route,
                    depIata,
                    arrIata,
                    date
            ));
        }

        String internationalProvider = fallback(properties.getInternationalProvider(), fallback(properties.getProvider(), "aviationstack"));
        if (!"aviationstack".equalsIgnoreCase(internationalProvider)) {
            return FlightLookupResult.failed(withCtripFallback(
                    "当前国际航线仅支持 aviationstack 服务商，请检查 app.flight.international-provider 配置。",
                    route,
                    depIata,
                    arrIata,
                    date
            ));
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return FlightLookupResult.failed(withCtripFallback(
                    "国际机票查询已触发，但尚未配置 aviationstack API Key。请在 application.yml 配置 app.flight.api-key，或设置环境变量 APP_FLIGHT_API_KEY。",
                    route,
                    depIata,
                    arrIata,
                    date
            ));
        }

        try {
            String url = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                    .queryParam("access_key", properties.getApiKey().trim())
                    .queryParam("dep_iata", depIata)
                    .queryParam("arr_iata", arrIata)
                    .queryParam("flight_date", date.format(DATE_FORMATTER))
                    .queryParam("limit", Math.max(1, properties.getLimit()))
                    .toUriString();

            FlightApiResponse response = restTemplate.getForObject(url, FlightApiResponse.class);
            if (response == null) {
                return FlightLookupResult.failed(withCtripFallback(
                        "机票服务未返回有效数据，请稍后再试。",
                        route,
                        depIata,
                        arrIata,
                        date
                ));
            }
            if (response.error != null) {
                return FlightLookupResult.failed(withCtripFallback(
                        "机票服务调用失败：" + fallback(response.error.message, "服务商返回错误"),
                        route,
                        depIata,
                        arrIata,
                        date
                ));
            }
            if (response.data == null || response.data.isEmpty()) {
                return FlightLookupResult.failed(withCtripFallback(
                        "未查询到符合条件的航班，请调整日期或城市后重试。",
                        route,
                        depIata,
                        arrIata,
                        date
                ));
            }

            List<FlightItem> flights = response.data.subList(0, Math.min(response.data.size(), Math.max(1, properties.getLimit())));
            StringBuilder builder = new StringBuilder();
            builder.append("机票查询：")
                    .append(route.departure()).append("(").append(depIata).append(")")
                    .append(" -> ")
                    .append(route.arrival()).append("(").append(arrIata).append(")")
                    .append("，出行日期 ").append(date.format(DATE_FORMATTER))
                    .append("，参考结果 ").append(flights.size()).append(" 条。");

            int index = 1;
            for (FlightItem item : flights) {
                builder.append('\n')
                        .append(index++).append(") ")
                        .append(fallback(item.airline == null ? null : item.airline.name, "未知航司"))
                        .append(' ')
                        .append(resolveFlightNo(item))
                        .append(" | ")
                        .append("状态 ").append(fallback(item.flight_status, "未知"))
                        .append(" | ")
                        .append("起飞 ").append(compactTime(item.departure == null ? null : item.departure.scheduled))
                        .append(" | ")
                        .append("降落 ").append(compactTime(item.arrival == null ? null : item.arrival.scheduled));
            }
            builder.append('\n')
                    .append("携程比价链接：")
                    .append(buildCtripUrl(depIata, arrIata, date));
            return FlightLookupResult.success(builder.toString());
        } catch (Exception ex) {
            return FlightLookupResult.failed(withCtripFallback(
                    "机票服务调用失败，请稍后再试。",
                    route,
                    depIata,
                    arrIata,
                    date
            ));
        }
    }
    private boolean isDomesticRoute(FlightRoute route, String depIata, String arrIata) {
        if (isMainlandChinaIata(depIata) && isMainlandChinaIata(arrIata)) {
            return true;
        }
        return isLikelyDomesticCityName(route.departure()) && isLikelyDomesticCityName(route.arrival());
    }

    private boolean isMainlandChinaIata(String iata) {
        return iata != null && MAINLAND_CHINA_IATA.contains(iata.toUpperCase(Locale.ROOT));
    }

    private boolean isLikelyDomesticCityName(String city) {
        if (city == null || city.isBlank()) {
            return false;
        }
        String normalized = TravelGeoUtils.normalizeCityToken(city).toLowerCase(Locale.ROOT);
        if (CITY_TO_IATA.containsKey(normalized) && isMainlandChinaIata(CITY_TO_IATA.get(normalized))) {
            return true;
        }
        if (containsAny(normalized, "tokyo", "osaka", "seoul", "singapore", "london", "paris",
                "new york", "newyork", "los angeles", "losangeles", "san francisco", "sanfrancisco",
                "sydney", "dubai", "hong kong", "hongkong", "macau", "macao", "taipei")) {
            return false;
        }
        if (containsAny(normalized, "东京", "大阪", "首尔", "新加坡", "伦敦", "巴黎", "纽约", "洛杉矶",
                "旧金山", "悉尼", "迪拜", "香港", "澳门", "台北")) {
            return false;
        }
        return normalized.codePoints().anyMatch(Character::isIdeographic);
    }

    private boolean containsAny(String text, String... keys) {
        if (text == null || text.isBlank() || keys == null) {
            return false;
        }
        for (String key : keys) {
            if (key != null && !key.isBlank() && text.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private String buildCtripUrl(String depToken, String arrToken, LocalDate date) {
        String template = fallback(properties.getCtripBaseUrl(), DEFAULT_CTRIP_BASE_URL).trim();
        if (template.isBlank()) {
            template = DEFAULT_CTRIP_BASE_URL;
        }

        String routeUrl;
        if (template.contains("{dep}") && template.contains("{arr}")) {
            routeUrl = template.replace("{dep}", encodePathToken(depToken)).replace("{arr}", encodePathToken(arrToken));
        } else {
            routeUrl = DEFAULT_CTRIP_BASE_URL.replace("{dep}", encodePathToken(depToken)).replace("{arr}", encodePathToken(arrToken));
        }

        return UriComponentsBuilder.fromUriString(routeUrl)
                .queryParam("depdate", date.format(DATE_FORMATTER))
                .toUriString();
    }


    private String withCtripFallback(String message, FlightRoute route, String depIata, String arrIata, LocalDate date) {
        if (route == null || date == null) {
            return message;
        }
        String depToken = depIata == null || depIata.isBlank() ? route.departure() : depIata;
        String arrToken = arrIata == null || arrIata.isBlank() ? route.arrival() : arrIata;
        return message + "\n可直接改用携程查询：" + buildCtripUrl(depToken, arrToken, date);
    }
    private String encodePathToken(String token) {
        String safe = fallback(token, "").trim();
        if (safe.isBlank()) {
            return "";
        }
        return URLEncoder.encode(safe, StandardCharsets.UTF_8);
    }

    private FlightRoute extractRoute(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }

        String departureStructured = cleanupName(TravelGeoUtils.extractStructuredValue(question, "departure_city", "departurecity", "from_city"));
        String destinationStructured = cleanupName(TravelGeoUtils.extractStructuredValue(question, "destination_city", "destinationcity", "to_city", "arrival_city"));
        if (!departureStructured.isBlank() && !destinationStructured.isBlank()) {
            return new FlightRoute(departureStructured, destinationStructured);
        }

        String searchLine = extractLabeledValue(question, "机票检索语句", "车票检索语句", "flight_query");
        FlightRoute fromSearchLine = matchRoute(searchLine);
        if (fromSearchLine != null) {
            return fromSearchLine;
        }

        String departure = cleanupName(extractLabeledValue(question, "出发地"));
        String destination = cleanupName(extractLabeledValue(question, "目的地或需求", "目的地"));
        if (!departure.isBlank() && !destination.isBlank()) {
            return new FlightRoute(departure, destination);
        }

        return matchRoute(question);
    }

    private FlightRoute matchRoute(String text) {
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
        return new FlightRoute(from, to);
    }

    private LocalDate extractDate(String question) {
        String structuredDateText = TravelGeoUtils.extractStructuredValue(question, "travel_start_date", "travelstartdate", "start_date", "departure_date");
        LocalDate fromStructured = parseDateFromText(structuredDateText);
        if (fromStructured != null) {
            return fromStructured;
        }

        String fromSearchLine = extractLabeledValue(question, "机票检索语句", "车票检索语句", "出行日期");
        LocalDate structuredDate = parseDateFromText(fromSearchLine);
        if (structuredDate != null) {
            return structuredDate;
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

        Matcher departMatcher = DEPART_PATTERN.matcher(text);
        if (departMatcher.find()) {
            try {
                return LocalDate.parse(departMatcher.group(1), DATE_FORMATTER);
            } catch (Exception ignored) {
                // continue
            }
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

    private String toIata(String city) {
        if (city == null || city.isBlank()) {
            return null;
        }
        String inlineIata = extractIataToken(city);
        if (inlineIata != null) {
            return inlineIata;
        }
        String normalizedCity = TravelGeoUtils.normalizeCityToken(city);
        String normalized = normalizedCity.toLowerCase(Locale.ROOT);
        String normalizedInlineIata = extractIataToken(normalizedCity);
        if (normalizedInlineIata != null) {
            return normalizedInlineIata;
        }
        String mapped = CITY_TO_IATA.get(normalized);
        if (mapped != null && !mapped.isBlank()) {
            return mapped;
        }
        mapped = CITY_TO_IATA.get(city.toLowerCase(Locale.ROOT));
        if (mapped != null && !mapped.isBlank()) {
            return mapped;
        }
        for (Map.Entry<String, String> entry : CITY_TO_IATA.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        if (city.matches("^[A-Za-z]{3}$")) {
            return city.toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private String extractIataToken(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = IATA_TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group(1).toUpperCase(Locale.ROOT);
            if (candidate.length() == 3) {
                return candidate;
            }
        }
        return null;
    }

    private String cleanupName(String city) {
        if (city == null) {
            return "";
        }
        String cleaned = city.trim()
                .replace("机场", "")
                .replace("市", "")
                .replace("省", "")
                .replace("旅行", "")
                .replace("旅游", "")
                .replace("行程", "")
                .replace("方案", "")
                .replace("，", " ")
                .replace(",", " ")
                .replace("。", " ")
                .trim();
        String[] parts = cleaned.split("\\s+");
        String normalized = parts.length == 0 ? "" : parts[0];
        return TravelGeoUtils.normalizeCityToken(normalized);
    }

    private String resolveFlightNo(FlightItem item) {
        if (item == null || item.flight == null) {
            return "班次未知";
        }
        String iata = fallback(item.flight.iata, "");
        if (!iata.isBlank()) {
            return iata;
        }
        String number = fallback(item.flight.number, "");
        return number.isBlank() ? "班次未知" : number;
    }

    private String compactTime(String isoText) {
        if (isoText == null || isoText.isBlank()) {
            return "未知";
        }
        try {
            return OffsetDateTime.parse(isoText).toLocalTime().withSecond(0).withNano(0).toString();
        } catch (DateTimeParseException ex) {
            if (isoText.length() >= 16) {
                return isoText.substring(11, 16);
            }
            return isoText;
        }
    }

    private String extractLabeledValue(String text, String... labels) {
        return TravelGeoUtils.extractLabeledValue(text, labels);
    }

    private String fallback(String text, String defaultValue) {
        return text == null || text.isBlank() ? defaultValue : text;
    }

    private static Map<String, String> buildCityIataMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("北京", "PEK");
        map.put("上海", "SHA");
        map.put("广州", "CAN");
        map.put("深圳", "SZX");
        map.put("杭州", "HGH");
        map.put("南京", "NKG");
        map.put("成都", "CTU");
        map.put("重庆", "CKG");
        map.put("西安", "XIY");
        map.put("武汉", "WUH");
        map.put("厦门", "XMN");
        map.put("青岛", "TAO");
        map.put("三亚", "SYX");
        map.put("昆明", "KMG");
        map.put("天津", "TSN");
        map.put("郑州", "CGO");
        map.put("长沙", "CSX");
        map.put("福州", "FOC");
        map.put("海口", "HAK");
        map.put("大连", "DLC");
        map.put("宁波", "NGB");
        map.put("沈阳", "SHE");
        map.put("乌鲁木齐", "URC");
        map.put("合肥", "HFE");
        map.put("济南", "TNA");
        map.put("泉州", "JJN");
        map.put("兰州", "LHW");

        map.put("东京", "TYO");
        map.put("大阪", "OSA");
        map.put("首尔", "SEL");
        map.put("曼谷", "BKK");
        map.put("新加坡", "SIN");
        map.put("伦敦", "LON");
        map.put("巴黎", "PAR");
        map.put("纽约", "NYC");
        map.put("洛杉矶", "LAX");
        map.put("旧金山", "SFO");
        map.put("悉尼", "SYD");
        map.put("迪拜", "DXB");
        map.put("香港", "HKG");
        map.put("澳门", "MFM");
        map.put("台北", "TPE");

        map.put("beijing", "PEK");
        map.put("shanghai", "SHA");
        map.put("guangzhou", "CAN");
        map.put("shenzhen", "SZX");
        map.put("hangzhou", "HGH");
        map.put("nanjing", "NKG");
        map.put("chengdu", "CTU");
        map.put("chongqing", "CKG");
        map.put("xian", "XIY");
        map.put("wuhan", "WUH");
        map.put("xiamen", "XMN");
        map.put("qingdao", "TAO");
        map.put("sanya", "SYX");
        map.put("kunming", "KMG");
        map.put("ningbo", "NGB");
        map.put("shenyang", "SHE");
        map.put("urumqi", "URC");
        map.put("hefei", "HFE");
        map.put("jinan", "TNA");
        map.put("quanzhou", "JJN");
        map.put("lanzhou", "LHW");

        map.put("tokyo", "TYO");
        map.put("osaka", "OSA");
        map.put("seoul", "SEL");
        map.put("bangkok", "BKK");
        map.put("singapore", "SIN");
        map.put("london", "LON");
        map.put("paris", "PAR");
        map.put("new york", "NYC");
        map.put("newyork", "NYC");
        map.put("los angeles", "LAX");
        map.put("losangeles", "LAX");
        map.put("san francisco", "SFO");
        map.put("sanfrancisco", "SFO");
        map.put("sydney", "SYD");
        map.put("dubai", "DXB");
        map.put("hong kong", "HKG");
        map.put("hongkong", "HKG");
        map.put("macau", "MFM");
        map.put("macao", "MFM");
        map.put("taipei", "TPE");
        return map;
    }

    public record FlightLookupResult(boolean success, String summary) {
        public static FlightLookupResult success(String summary) {
            return new FlightLookupResult(true, summary == null ? "" : summary);
        }

        public static FlightLookupResult failed(String summary) {
            return new FlightLookupResult(false, summary == null ? "" : summary);
        }
    }

    private record FlightRoute(String departure, String arrival) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FlightApiResponse {
        public List<FlightItem> data;
        public FlightApiError error;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FlightApiError {
        public String message;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FlightItem {
        public String flight_status;
        public AirlineInfo airline;
        public FlightInfo flight;
        public FlightEndpoint departure;
        public FlightEndpoint arrival;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AirlineInfo {
        public String name;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FlightInfo {
        public String iata;
        public String number;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FlightEndpoint {
        public String scheduled;
    }
}

