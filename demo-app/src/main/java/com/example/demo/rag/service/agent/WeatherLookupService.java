package com.example.demo.rag.service.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WeatherLookupService {

    private static final String GEOCODE_API = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String FORECAST_API = "https://api.open-meteo.com/v1/forecast";

    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})");
    private static final Pattern IN_CITY_PATTERN = Pattern.compile("(?:在|去|到)?\\s*([\\p{IsHan}A-Za-z]{2,20})\\s*(?:天气|气温|降雨|下雨)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOCATION_TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}A-Za-z]{2,20}");

    private static final Set<String> STOP_WORDS = Set.of(
            "天气", "气温", "温度", "降水", "下雨", "查询", "帮我", "请问", "看看", "行程", "出发", "目的地",
            "预算", "人数", "偏好", "车票", "火车", "高铁", "机票", "航班", "酒店", "住宿", "方案"
    );

    private static final Map<String, GeoPoint> CITY_CENTER_FALLBACK = buildCityCenterFallback();

    private final RestTemplate restTemplate;

    public WeatherLookupService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(8))
                .build();
    }

    /**
     * 兼容旧调用名，内部已切换为“按出行时间段天气”查询。
     */
    public WeatherLookupResult lookupCurrentWeather(String question) {
        return lookupTravelWeather(question);
    }

    /**
     * 优先按 travel_start_date/travel_end_date 查询行程天气；未提供日期时回退当前天气。
     */
    public WeatherLookupResult lookupTravelWeather(String question) {
        List<String> candidates = extractLocationCandidates(question);
        if (candidates.isEmpty()) {
            return WeatherLookupResult.failed("已识别天气查询意图，但未解析到城市，请补充“目的地 + 出行日期”。");
        }

        DateRange dateRange = extractDateRange(question);
        for (String city : candidates) {
            Optional<GeoPoint> geoPoint = geocodeCity(city);
            if (geoPoint.isEmpty()) {
                continue;
            }
            if (dateRange.hasDate()) {
                WeatherLookupResult window = queryTravelWindowWeather(geoPoint.get(), dateRange);
                if (window.success()) {
                    return window;
                }
            }

            WeatherLookupResult current = queryCurrentWeather(geoPoint.get());
            if (current.success()) {
                if (dateRange.hasDate()) {
                    return WeatherLookupResult.success(current.summary() + "\n注：未获取到指定日期区间天气，已回退为当前天气。");
                }
                return current;
            }
        }

        return WeatherLookupResult.failed("天气查询失败：未匹配到可查询城市，或天气服务暂时不可用。");
    }

    private WeatherLookupResult queryTravelWindowWeather(GeoPoint geoPoint, DateRange dateRange) {
        LocalDate start = dateRange.start();
        LocalDate end = dateRange.end();
        if (start == null) {
            return WeatherLookupResult.failed("未提供有效出行开始日期。");
        }
        if (end == null || end.isBefore(start)) {
            end = start;
        }

        try {
            String url = UriComponentsBuilder.fromUriString(FORECAST_API)
                    .queryParam("latitude", geoPoint.latitude())
                    .queryParam("longitude", geoPoint.longitude())
                    .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum")
                    .queryParam("timezone", "Asia/Shanghai")
                    .queryParam("start_date", start)
                    .queryParam("end_date", end)
                    .toUriString();

            ForecastResponse response = restTemplate.getForObject(url, ForecastResponse.class);
            if (response == null || response.daily == null || response.daily.time == null || response.daily.time.isEmpty()) {
                return queryUpcomingAsFallback(geoPoint, start, end);
            }

            return buildDailySummary(geoPoint, response.daily, start, end, false);
        } catch (Exception ex) {
            return queryUpcomingAsFallback(geoPoint, start, end);
        }
    }

    private WeatherLookupResult queryUpcomingAsFallback(GeoPoint geoPoint, LocalDate requestedStart, LocalDate requestedEnd) {
        try {
            String url = UriComponentsBuilder.fromUriString(FORECAST_API)
                    .queryParam("latitude", geoPoint.latitude())
                    .queryParam("longitude", geoPoint.longitude())
                    .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum")
                    .queryParam("timezone", "Asia/Shanghai")
                    .queryParam("forecast_days", 7)
                    .toUriString();
            ForecastResponse fallback = restTemplate.getForObject(url, ForecastResponse.class);
            if (fallback == null || fallback.daily == null || fallback.daily.time == null || fallback.daily.time.isEmpty()) {
                return WeatherLookupResult.failed("天气服务未返回可用预报数据，请稍后重试。");
            }
            WeatherLookupResult result = buildDailySummary(geoPoint, fallback.daily, requestedStart, requestedEnd, true);
            return WeatherLookupResult.success(result.summary() + "\n提示：行程日期可能超出可预报窗口（通常约16天），已展示最近可查天气。");
        } catch (Exception ex) {
            return WeatherLookupResult.failed("天气服务调用失败，请稍后重试。");
        }
    }

    private WeatherLookupResult buildDailySummary(GeoPoint geoPoint,
                                                  DailyForecast daily,
                                                  LocalDate requestedStart,
                                                  LocalDate requestedEnd,
                                                  boolean fallbackWindow) {
        int count = daily.time == null ? 0 : daily.time.size();
        if (count == 0) {
            return WeatherLookupResult.failed("天气服务未返回可用预报数据，请稍后重试。");
        }

        LocalDate actualStart = parseDate(daily.time.get(0));
        LocalDate actualEnd = parseDate(daily.time.get(count - 1));
        if (actualStart == null || actualEnd == null) {
            return WeatherLookupResult.failed("天气服务返回了异常日期格式，请稍后重试。");
        }

        long requestedDays = ChronoUnit.DAYS.between(requestedStart, requestedEnd) + 1;
        long actualDays = ChronoUnit.DAYS.between(actualStart, actualEnd) + 1;

        StringBuilder builder = new StringBuilder();
        builder.append("天气查询：")
                .append(geoPoint.displayName())
                .append("，行程 ")
                .append(requestedStart)
                .append(" 至 ")
                .append(requestedEnd)
                .append("（共")
                .append(Math.max(1, requestedDays))
                .append("天）");

        if (fallbackWindow || actualStart.isAfter(requestedStart) || actualEnd.isBefore(requestedEnd)) {
            builder.append("\n可用预报区间：")
                    .append(actualStart)
                    .append(" 至 ")
                    .append(actualEnd)
                    .append("（共")
                    .append(Math.max(1, actualDays))
                    .append("天）");
        }

        int rows = Math.min(count, 10);
        for (int i = 0; i < rows; i++) {
            String day = valueAt(daily.time, i, "未知日期");
            Integer code = valueAt(daily.weather_code, i, null);
            Double max = valueAt(daily.temperature_2m_max, i, null);
            Double min = valueAt(daily.temperature_2m_min, i, null);
            Double rain = valueAt(daily.precipitation_sum, i, null);

            builder.append('\n')
                    .append(i + 1)
                    .append(") ")
                    .append(day)
                    .append("：")
                    .append(weatherCodeDescription(code))
                    .append("，")
                    .append(formatTemp(min))
                    .append("~")
                    .append(formatTemp(max))
                    .append("，降水")
                    .append(formatRain(rain));
        }

        return WeatherLookupResult.success(builder.toString());
    }

    private WeatherLookupResult queryCurrentWeather(GeoPoint geoPoint) {
        try {
            String url = UriComponentsBuilder.fromUriString(FORECAST_API)
                    .queryParam("latitude", geoPoint.latitude())
                    .queryParam("longitude", geoPoint.longitude())
                    .queryParam("current", "temperature_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m")
                    .queryParam("timezone", "Asia/Shanghai")
                    .toUriString();

            ForecastResponse response = restTemplate.getForObject(url, ForecastResponse.class);
            if (response == null || response.current == null) {
                return WeatherLookupResult.failed("天气服务未返回当前天气数据。");
            }

            CurrentWeather current = response.current;
            String summary = String.format(
                    Locale.ROOT,
                    "天气查询：%s 当前%s，气温%s，体感%s，降水%s，风速%s。",
                    geoPoint.displayName(),
                    weatherCodeDescription(current.weather_code),
                    formatTemp(current.temperature_2m),
                    formatTemp(current.apparent_temperature),
                    formatRain(current.precipitation),
                    formatWind(current.wind_speed_10m)
            );
            return WeatherLookupResult.success(summary);
        } catch (Exception ex) {
            return WeatherLookupResult.failed("天气服务调用失败，请稍后重试。");
        }
    }

    private Optional<GeoPoint> geocodeCity(String city) {
        if (city == null || city.isBlank()) {
            return Optional.empty();
        }

        for (String candidate : expandCityCandidates(city)) {
            try {
                String url = UriComponentsBuilder.fromUriString(GEOCODE_API)
                        .queryParam("name", candidate)
                        .queryParam("count", 1)
                        .queryParam("language", "zh")
                        .queryParam("format", "json")
                        .toUriString();
                GeocodeResponse response = restTemplate.getForObject(url, GeocodeResponse.class);
                if (response != null && response.results != null && !response.results.isEmpty()) {
                    GeocodeItem first = response.results.get(0);
                    if (first != null && first.latitude != null && first.longitude != null && first.name != null && !first.name.isBlank()) {
                        String display = first.name;
                        if (first.admin1 != null && !first.admin1.isBlank() && !first.admin1.equals(first.name)) {
                            display += "(" + first.admin1 + ")";
                        }
                        return Optional.of(new GeoPoint(display, first.latitude, first.longitude));
                    }
                }
            } catch (Exception ignored) {
                // try next candidate
            }
        }

        for (String candidate : expandCityCandidates(city)) {
            GeoPoint fallback = CITY_CENTER_FALLBACK.get(candidate.toLowerCase(Locale.ROOT));
            if (fallback != null) {
                return Optional.of(fallback);
            }
        }

        return Optional.empty();
    }

    private List<String> extractLocationCandidates(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String weatherCity = extractStructuredValue(question, "weather_city");
        if (weatherCity != null && !weatherCity.isBlank()) {
            addCandidate(candidates, weatherCity);
            return new ArrayList<>(candidates);
        }

        addCandidate(candidates, extractStructuredValue(question, "destination_city", "destinationcity", "weather_city", "city"));
        addCandidate(candidates, extractStructuredValue(question, "departure_city", "departurecity", "from_city"));

        Matcher inMatcher = IN_CITY_PATTERN.matcher(question);
        while (inMatcher.find()) {
            addCandidate(candidates, inMatcher.group(1));
        }

        Matcher matcher = LOCATION_TOKEN_PATTERN.matcher(question);
        while (matcher.find()) {
            String token = normalizeCityName(matcher.group());
            if (token.length() < 2 || STOP_WORDS.contains(token)) {
                continue;
            }
            addCandidate(candidates, token);
        }

        return new ArrayList<>(candidates);
    }

    private DateRange extractDateRange(String question) {
        LocalDate start = parseDate(extractStructuredValue(question,
                "travel_start_date", "travelstartdate", "start_date", "departure_date"));
        LocalDate end = parseDate(extractStructuredValue(question,
                "travel_end_date", "travelenddate", "end_date", "return_date"));

        if (start != null && end == null) {
            end = start;
        }
        if (start != null && end != null && end.isBefore(start)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }
        if (start != null) {
            return new DateRange(start, end);
        }

        List<LocalDate> dates = new ArrayList<>();
        Matcher matcher = DATE_PATTERN.matcher(nullToEmpty(question));
        while (matcher.find()) {
            try {
                dates.add(LocalDate.of(
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3))
                ));
            } catch (Exception ignored) {
                // ignore
            }
        }
        if (dates.isEmpty()) {
            return DateRange.empty();
        }
        dates.sort(LocalDate::compareTo);
        LocalDate s = dates.get(0);
        LocalDate e = dates.size() >= 2 ? dates.get(1) : s;
        if (e.isBefore(s)) {
            e = s;
        }
        return new DateRange(s, e);
    }

    private List<String> expandCityCandidates(String city) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (city != null && !city.isBlank()) {
            String cleaned = normalizeCityName(city);
            set.add(cleaned);
            set.add(cleaned.toLowerCase(Locale.ROOT));
            set.add(cleaned + "市");
            set.add(city.trim());
            set.add(city.trim().toLowerCase(Locale.ROOT));
        }
        return new ArrayList<>(set);
    }

    private void addCandidate(Set<String> set, String city) {
        if (city == null || city.isBlank()) {
            return;
        }
        String normalized = normalizeCityName(city);
        if (normalized.length() < 2 || STOP_WORDS.contains(normalized)) {
            return;
        }
        set.add(normalized);
    }

    private String normalizeCityName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim()
                .replace('（', ' ')
                .replace('）', ' ')
                .replace('(', ' ')
                .replace(')', ' ')
                .replace("省", "")
                .replace("市", "")
                .replace("自治区", "")
                .replace("特别行政区", "")
                .replace("地区", "")
                .replace("天气", "")
                .replace("出发地", "")
                .replace("目的地", "")
                .replaceAll("\\s+", "")
                .trim();
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

    private LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = DATE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return LocalDate.of(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private String weatherCodeDescription(Integer code) {
        if (code == null) {
            return "天气未知";
        }
        return switch (code) {
            case 0 -> "晴";
            case 1 -> "大致晴";
            case 2 -> "局部多云";
            case 3 -> "阴";
            case 45, 48 -> "有雾";
            case 51, 53, 55 -> "毛毛雨";
            case 56, 57 -> "冻毛毛雨";
            case 61 -> "小雨";
            case 63 -> "中雨";
            case 65 -> "大雨";
            case 66, 67 -> "冻雨";
            case 71 -> "小雪";
            case 73 -> "中雪";
            case 75 -> "大雪";
            case 77 -> "冰粒";
            case 80 -> "小阵雨";
            case 81 -> "中阵雨";
            case 82 -> "强阵雨";
            case 85 -> "小阵雪";
            case 86 -> "强阵雪";
            case 95 -> "雷暴";
            case 96, 99 -> "雷暴伴冰雹";
            default -> "天气代码" + code;
        };
    }

    private String formatTemp(Double value) {
        if (value == null) {
            return "未知";
        }
        return String.format(Locale.ROOT, "%.1f℃", value);
    }

    private String formatRain(Double value) {
        if (value == null) {
            return "未知";
        }
        return String.format(Locale.ROOT, "%.1fmm", value);
    }

    private String formatWind(Double value) {
        if (value == null) {
            return "未知";
        }
        return String.format(Locale.ROOT, "%.1fkm/h", value);
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private <T> T valueAt(List<T> values, int index, T defaultValue) {
        if (values == null || index < 0 || index >= values.size()) {
            return defaultValue;
        }
        T item = values.get(index);
        return item == null ? defaultValue : item;
    }

    private static Map<String, GeoPoint> buildCityCenterFallback() {
        Map<String, GeoPoint> map = new LinkedHashMap<>();
        map.put("武汉", new GeoPoint("武汉(湖北)", 30.5928, 114.3055));
        map.put("襄阳", new GeoPoint("襄阳(湖北)", 32.0100, 112.1160));
        map.put("上海", new GeoPoint("上海", 31.2304, 121.4737));
        map.put("北京", new GeoPoint("北京", 39.9042, 116.4074));
        map.put("广州", new GeoPoint("广州", 23.1291, 113.2644));
        map.put("深圳", new GeoPoint("深圳", 22.5431, 114.0579));
        map.put("杭州", new GeoPoint("杭州", 30.2741, 120.1551));
        map.put("南京", new GeoPoint("南京", 32.0603, 118.7969));
        map.put("成都", new GeoPoint("成都", 30.5728, 104.0668));
        map.put("重庆", new GeoPoint("重庆", 29.5630, 106.5516));
        map.put("西安", new GeoPoint("西安", 34.3416, 108.9398));
        map.put("wuhan", new GeoPoint("Wuhan", 30.5928, 114.3055));
        map.put("xiangyang", new GeoPoint("Xiangyang", 32.0100, 112.1160));
        map.put("shanghai", new GeoPoint("Shanghai", 31.2304, 121.4737));
        map.put("beijing", new GeoPoint("Beijing", 39.9042, 116.4074));
        return map;
    }

    public record WeatherLookupResult(boolean success, String summary) {
        public static WeatherLookupResult success(String summary) {
            return new WeatherLookupResult(true, summary == null ? "" : summary);
        }

        public static WeatherLookupResult failed(String summary) {
            return new WeatherLookupResult(false, summary == null ? "" : summary);
        }
    }

    private record DateRange(LocalDate start, LocalDate end) {
        static DateRange empty() {
            return new DateRange(null, null);
        }

        boolean hasDate() {
            return start != null;
        }
    }

    private record GeoPoint(String displayName, double latitude, double longitude) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GeocodeResponse {
        public List<GeocodeItem> results;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GeocodeItem {
        public String name;
        public String admin1;
        public Double latitude;
        public Double longitude;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ForecastResponse {
        public CurrentWeather current;
        public DailyForecast daily;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class CurrentWeather {
        public Double temperature_2m;
        public Double apparent_temperature;
        public Double precipitation;
        public Integer weather_code;
        public Double wind_speed_10m;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DailyForecast {
        public List<String> time;
        public List<Integer> weather_code;
        public List<Double> temperature_2m_max;
        public List<Double> temperature_2m_min;
        public List<Double> precipitation_sum;
    }
}
