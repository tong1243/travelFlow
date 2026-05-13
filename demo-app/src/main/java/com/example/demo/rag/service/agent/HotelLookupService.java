package com.example.demo.rag.service.agent;

import com.example.demo.rag.config.HotelLookupProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class HotelLookupService {

    private static final String NOMINATIM_SEARCH_URL = "https://nominatim.openstreetmap.org/search";
    private static final String BOOKING_SEARCH_URL = "https://www.booking.com/searchresults.zh-cn.html";
    private static final String CTRIP_SEARCH_URL = "https://hotels.ctrip.com/hotels/list";
    private static final String OSM_MAP_SEARCH_URL = "https://www.openstreetmap.org/search";
    private static final Set<String> DOMESTIC_CITY_HINTS = Set.of(
            "\u4e2d\u56fd",
            "china",
            "\u5317\u4eac",
            "\u4e0a\u6d77",
            "\u5e7f\u5dde",
            "\u6df1\u5733",
            "\u6210\u90fd",
            "\u91cd\u5e86",
            "\u897f\u5b89",
            "\u6b66\u6c49",
            "\u5357\u4eac",
            "\u676d\u5dde",
            "\u82cf\u5dde",
            "\u9752\u5c9b",
            "\u53a6\u95e8",
            "\u4e09\u4e9a",
            "\u6606\u660e",
            "\u957f\u6c99",
            "\u90d1\u5dde",
            "\u9999\u6e2f",
            "\u6fb3\u95e8",
            "\u53f0\u5317",
            "beijing",
            "shanghai",
            "guangzhou",
            "shenzhen",
            "chengdu",
            "chongqing",
            "xian",
            "xi'an",
            "wuhan",
            "nanjing",
            "hangzhou",
            "suzhou",
            "qingdao",
            "xiamen",
            "sanya",
            "kunming",
            "changsha",
            "zhengzhou",
            "hong kong",
            "hongkong",
            "macau",
            "macao",
            "taipei"
    );

    private static final Pattern CITY_PATTERN = Pattern.compile("(?:在|去|到)?\\s*([\\p{IsHan}A-Za-z]{2,20})\\s*(?:住哪|住宿|酒店|hotel|hostel)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}A-Za-z]{2,20}");
    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})");
    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile("(\\d{1,2})月(\\d{1,2})日");
    private static final Pattern PRICE_RANGE_PATTERN = Pattern.compile("(\\d{2,5})\\s*(?:-|~|～|—|–|－)\\s*(\\d{2,5})");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final Set<String> CITY_STOP_WORDS = Set.of(
            "酒店", "住宿", "民宿", "宾馆", "推荐", "安排", "查询", "预订", "帮我", "行程", "方案", "预算", "人数", "天气", "车票", "机票"
    );
    private static final Set<String> BUSY_AREA_HINTS = Set.of(
            "市中心", "商圈", "地铁站", "火车站", "高铁站", "机场", "步行街", "广场", "cbd", "center", "central", "station", "airport"
    );
    private static final List<String> DEFAULT_BUSINESS_AREAS = List.of("市中心", "核心商圈", "地铁枢纽", "高铁站周边", "景区入口", "机场快线沿线");
    private static final List<String> DEFAULT_QUIET_AREAS = List.of("大学城周边", "居民区腹地", "公园周边", "次中心社区", "江景安静区", "非主干道内街");

    private static final Map<String, GeoPoint> CITY_CENTER_FALLBACK = buildCityCenterFallback();

    private final HotelLookupProperties properties;
    private final RestTemplate restTemplate;

    public HotelLookupService(HotelLookupProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())))
                .readTimeout(Duration.ofSeconds(Math.max(1, properties.getReadTimeoutSeconds())))
                .build();
    }

    public HotelLookupResult lookupHotels(String question) {
        return lookupHotels(question, null);
    }

    public HotelLookupResult lookupHotels(String question, String requestedPriceRange) {
        if (!properties.isEnabled()) {
            return HotelLookupResult.failed("酒店查询已在系统配置中关闭。");
        }
        HotelPreference preference = extractHotelPreference(question);
        PriceRange priceRange = resolvePriceRange(question, requestedPriceRange);
        if (preference == HotelPreference.BOOKED) {
            return HotelLookupResult.success("酒店偏好：已预定，本次已跳过酒店推荐。");
        }

        String city = extractCity(question);
        if (city.isBlank()) {
            return HotelLookupResult.failed("已识别酒店查询意图，但未解析出城市。");
        }

        Optional<GeoPoint> cityPoint = geocodeCity(city);
        if (cityPoint.isEmpty()) {
            return HotelLookupResult.failed("已识别城市，但未能解析经纬度，建议换一种城市写法。");
        }

        DateWindow dateWindow = extractDateWindow(question);
        if (dateWindow.checkIn() == null) {
            LocalDate checkIn = LocalDate.now().plusDays(1);
            dateWindow = new DateWindow(checkIn, checkIn.plusDays(1));
        } else if (dateWindow.checkOut() == null || !dateWindow.checkOut().isAfter(dateWindow.checkIn())) {
            dateWindow = new DateWindow(dateWindow.checkIn(), dateWindow.checkIn().plusDays(1));
        }

        try {
            HotelQueryResult queryResult = queryHotels(city, cityPoint.get(), preference, priceRange);
            List<HotelItem> hotelItems = queryResult.hotels();
            String source = queryResult.source();
            List<HotelItem> fallbackItems = buildFallbackHotels(city, cityPoint.get(), preference, priceRange);
            if (hotelItems.isEmpty()) {
                hotelItems = fallbackItems;
                source = "Fallback";
            } else if (hotelItems.size() < 3 && !fallbackItems.isEmpty()) {
                List<HotelItem> merged = new ArrayList<>(hotelItems);
                merged.addAll(fallbackItems);
                hotelItems = deduplicateAndSort(merged, preference, priceRange);
                source = source + "+Fallback";
            }
            if (hotelItems.isEmpty()) {
                return HotelLookupResult.failed("未查询到可展示的酒店候选。可换一个城市或放宽条件后重试。");
            }

            int target = Math.max(3, properties.getLimit());
            int limit = Math.min(Math.max(1, target), hotelItems.size());
            StringBuilder builder = new StringBuilder();
            builder.append("酒店查询：")
                    .append(cityPoint.get().displayName())
                    .append("，入住 ")
                    .append(dateWindow.checkIn().format(DATE_FORMATTER))
                    .append("，离店 ")
                    .append(dateWindow.checkOut().format(DATE_FORMATTER))
                    .append("，参考结果 ")
                    .append(limit)
                    .append(" 条（数据源 ")
                    .append(source)
                    .append("），偏好 ")
                    .append(preference.displayLabel())
                    .append("，价格偏好 ")
                    .append(priceRange == null ? "不限" : priceRange.text() + " 元/晚")
                    .append("。")
                    .append("\n提示：价格和房态请以下方预订链接实时页面为准。\n");

            for (int i = 0; i < limit; i++) {
                HotelItem item = hotelItems.get(i);
                BookingTarget bookingTarget = resolveBookingTarget(city, item.name(), dateWindow, priceRange);
                String locationLink = buildMapSearchLink(city, item.name());

                builder.append(i + 1)
                        .append(") ")
                        .append(item.name())
                        .append(" | 距离约 ")
                        .append(formatKm(item.distanceKm()))
                        .append(" | 地址 ")
                        .append(item.address())
                        .append(" | 星级 ")
                        .append(item.stars())
                        .append(" | 估价 ")
                        .append(estimatePriceLabel(item))
                        .append(" | \u5e73\u53f0 ")
                        .append(bookingTarget.platform())
                        .append(" | [去预订](")
                        .append(bookingTarget.url())
                        .append(") | [地图定位](")
                        .append(locationLink)
                        .append(")\n");
            }

            return HotelLookupResult.success(builder.toString().trim());
        } catch (Exception ex) {
            return HotelLookupResult.failed("酒店服务调用失败，请稍后重试。");
        }
    }

    private String extractCity(String question) {
        if (question == null || question.isBlank()) {
            return "";
        }

        String structuredDestination = TravelGeoUtils.normalizeCityToken(
                extractStructuredValue(question, "destination_city", "destinationcity", "hotel_city", "city")
        );
        if (!structuredDestination.isBlank() && !containsStopWord(structuredDestination)) {
            return structuredDestination;
        }

        String destination = TravelGeoUtils.normalizeCityToken(extractLabeledValue(question, "目的地", "目的地或需求"));
        if (!destination.isBlank() && !containsStopWord(destination)) {
            return destination;
        }

        Matcher cityMatcher = CITY_PATTERN.matcher(question);
        if (cityMatcher.find()) {
            return TravelGeoUtils.normalizeCityToken(cityMatcher.group(1));
        }

        Matcher tokenMatcher = TOKEN_PATTERN.matcher(question);
        while (tokenMatcher.find()) {
            String token = TravelGeoUtils.normalizeCityToken(tokenMatcher.group());
            if (token.length() < 2 || containsStopWord(token)) {
                continue;
            }
            return token;
        }
        return "";
    }

    private HotelPreference extractHotelPreference(String question) {
        if (question == null || question.isBlank()) {
            return HotelPreference.BUSINESS_DISTRICT;
        }
        String structured = extractStructuredValue(question, "hotel_preference", "hotelpreference");
        if (structured != null && !structured.isBlank()) {
            return HotelPreference.fromText(structured);
        }
        String labeled = extractLabeledValue(question, "酒店偏好", "酒店推荐");
        if (labeled != null && !labeled.isBlank()) {
            return HotelPreference.fromText(labeled);
        }
        return HotelPreference.BUSINESS_DISTRICT;
    }

    private DateWindow extractDateWindow(String question) {
        LocalDate checkIn = parseDateText(extractStructuredValue(question, "travel_start_date", "travelstartdate", "start_date", "check_in"));
        LocalDate checkOut = parseDateText(extractStructuredValue(question, "travel_end_date", "travelenddate", "end_date", "check_out"));

        if (checkIn != null && checkOut == null) {
            checkOut = checkIn.plusDays(1);
            return new DateWindow(checkIn, checkOut);
        }
        if (checkIn != null && checkOut != null) {
            if (!checkOut.isAfter(checkIn)) {
                checkOut = checkIn.plusDays(1);
            }
            return new DateWindow(checkIn, checkOut);
        }

        List<LocalDate> dates = new ArrayList<>();
        if (question != null) {
            Matcher fullMatcher = DATE_PATTERN.matcher(question);
            while (fullMatcher.find()) {
                try {
                    dates.add(LocalDate.of(
                            Integer.parseInt(fullMatcher.group(1)),
                            Integer.parseInt(fullMatcher.group(2)),
                            Integer.parseInt(fullMatcher.group(3))
                    ));
                } catch (Exception ignored) {
                    // ignore
                }
            }
            Matcher mdMatcher = MONTH_DAY_PATTERN.matcher(question);
            while (mdMatcher.find()) {
                try {
                    int month = Integer.parseInt(mdMatcher.group(1));
                    int day = Integer.parseInt(mdMatcher.group(2));
                    LocalDate now = LocalDate.now();
                    LocalDate candidate = LocalDate.of(now.getYear(), month, day);
                    if (candidate.isBefore(now)) {
                        candidate = candidate.plusYears(1);
                    }
                    dates.add(candidate);
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }

        if (dates.isEmpty()) {
            return new DateWindow(null, null);
        }

        dates.sort(LocalDate::compareTo);
        LocalDate fallbackIn = dates.get(0);
        LocalDate fallbackOut = dates.size() >= 2 ? dates.get(1) : fallbackIn.plusDays(1);
        if (!fallbackOut.isAfter(fallbackIn)) {
            fallbackOut = fallbackIn.plusDays(1);
        }
        return new DateWindow(fallbackIn, fallbackOut);
    }

    private LocalDate parseDateText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = DATE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            int y = Integer.parseInt(matcher.group(1));
            int m = Integer.parseInt(matcher.group(2));
            int d = Integer.parseInt(matcher.group(3));
            return LocalDate.of(y, m, d);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Optional<GeoPoint> geocodeCity(String city) {
        if (city == null || city.isBlank()) {
            return Optional.empty();
        }

        for (String candidate : expandCityCandidates(city)) {
            try {
                String geocodeUrl = fallback(properties.getGeocodeUrl(), "https://geocoding-api.open-meteo.com/v1/search");
                String url = UriComponentsBuilder.fromUriString(geocodeUrl)
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
                // try next
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

    private HotelQueryResult queryHotels(String cityName, GeoPoint cityPoint, HotelPreference preference, PriceRange priceRange) {
        String provider = fallback(properties.getProvider(), "overpass");
        if ("overpass".equalsIgnoreCase(provider)) {
            List<HotelItem> overpassHotels = queryHotelsByOverpass(cityPoint, preference, priceRange);
            if (!overpassHotels.isEmpty()) {
                return new HotelQueryResult(overpassHotels, "Overpass");
            }
        }

        List<HotelItem> nominatimHotels = queryHotelsByNominatim(cityName, cityPoint, preference, priceRange);
        if (!nominatimHotels.isEmpty()) {
            return new HotelQueryResult(nominatimHotels, "Nominatim");
        }
        return new HotelQueryResult(List.of(), "Fallback");
    }

    private List<HotelItem> queryHotelsByOverpass(GeoPoint cityPoint, HotelPreference preference, PriceRange priceRange) {
        int radius = Math.max(1000, properties.getRadiusMeters());
        String overpassQuery = """
                [out:json][timeout:15];
                (
                  node[\"tourism\"=\"hotel\"](around:%d,%f,%f);
                  way[\"tourism\"=\"hotel\"](around:%d,%f,%f);
                  relation[\"tourism\"=\"hotel\"](around:%d,%f,%f);
                );
                out center tags;
                """.formatted(
                radius, cityPoint.latitude(), cityPoint.longitude(),
                radius, cityPoint.latitude(), cityPoint.longitude(),
                radius, cityPoint.latitude(), cityPoint.longitude()
        );

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "travel-assistant/1.0");
            headers.setContentType(MediaType.TEXT_PLAIN);
            HttpEntity<String> entity = new HttpEntity<>(overpassQuery, headers);

            OverpassResponse response = restTemplate.postForObject(properties.getOverpassUrl(), entity, OverpassResponse.class);
            if (response == null || response.elements == null || response.elements.isEmpty()) {
                return List.of();
            }

            List<HotelItem> hotels = new ArrayList<>();
            for (OverpassElement element : response.elements) {
                if (element == null) {
                    continue;
                }
                double lat = element.lat != null ? element.lat : (element.center == null ? Double.NaN : element.center.lat);
                double lon = element.lon != null ? element.lon : (element.center == null ? Double.NaN : element.center.lon);
                if (Double.isNaN(lat) || Double.isNaN(lon)) {
                    continue;
                }

                Map<String, String> tags = element.tags == null ? Map.of() : element.tags;
                String name = fallback(tags.get("name"), "未命名酒店");
                String stars = fallback(tags.get("stars"), "未标注");
                String address = resolveAddress(tags);
                double distanceKm = distanceKm(cityPoint.latitude(), cityPoint.longitude(), lat, lon);

                hotels.add(new HotelItem(name, address, stars, distanceKm));
            }

            return deduplicateAndSort(hotels, preference, priceRange);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<HotelItem> queryHotelsByNominatim(String cityName, GeoPoint cityPoint, HotelPreference preference, PriceRange priceRange) {
        try {
            List<NominatimItem> mergedItems = new ArrayList<>();
            LinkedHashSet<String> queries = new LinkedHashSet<>();
            String normalizedCity = TravelGeoUtils.normalizeCityToken(cityName);
            String displayCity = cleanupCity(cityPoint.displayName());
            queries.add("hotel " + normalizedCity);
            queries.add(normalizedCity + " hotel");
            queries.add("住宿 " + normalizedCity);
            queries.add("hotel " + displayCity);
            queries.add(displayCity + " hotel");

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "travel-assistant/1.0");
            for (String query : queries) {
                if (query == null || query.isBlank()) {
                    continue;
                }
                String url = UriComponentsBuilder.fromUriString(NOMINATIM_SEARCH_URL)
                        .queryParam("format", "jsonv2")
                        .queryParam("limit", Math.max(10, properties.getLimit() * 3))
                        .queryParam("accept-language", "zh-CN")
                        .queryParam("q", query)
                        .toUriString();
                ResponseEntity<NominatimItem[]> entity = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        NominatimItem[].class
                );
                NominatimItem[] items = entity.getBody();
                if (items == null || items.length == 0) {
                    continue;
                }
                for (NominatimItem item : items) {
                    if (item != null) {
                        mergedItems.add(item);
                    }
                }
                if (mergedItems.size() >= Math.max(12, properties.getLimit() * 4)) {
                    break;
                }
            }
            if (mergedItems.isEmpty()) {
                return List.of();
            }
            List<HotelItem> hotels = new ArrayList<>();
            for (NominatimItem item : mergedItems) {
                if (item == null || item.lat == null || item.lon == null) {
                    continue;
                }
                double lat;
                double lon;
                try {
                    lat = Double.parseDouble(item.lat);
                    lon = Double.parseDouble(item.lon);
                } catch (Exception ex) {
                    continue;
                }

                String name = fallback(item.name, item.display_name);
                String address = fallback(item.display_name, "地址未标注");
                double distanceKm = distanceKm(cityPoint.latitude(), cityPoint.longitude(), lat, lon);
                hotels.add(new HotelItem(name, address, "未标注", distanceKm));
            }

            return deduplicateAndSort(hotels, preference, priceRange);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<HotelItem> buildFallbackHotels(String city, GeoPoint cityPoint, HotelPreference preference, PriceRange priceRange) {
        String normalizedCity = TravelGeoUtils.normalizeCityToken(city);
        String cityDisplay = normalizedCity.isBlank() ? cleanupCity(cityPoint.displayName()) : normalizedCity;
        if (cityDisplay.isBlank()) {
            return List.of();
        }
        List<String> areaHints = preference == HotelPreference.QUIET_PRIORITY ? DEFAULT_QUIET_AREAS : DEFAULT_BUSINESS_AREAS;
        List<HotelItem> result = new ArrayList<>();
        for (int i = 0; i < areaHints.size(); i++) {
            String area = areaHints.get(i);
            String name = cityDisplay + "·" + area + "酒店候选" + (i + 1);
            String address = cityDisplay + area + "（建议以预订页地图位置为准）";
            String stars = "参考4星";
            double distanceKm = 0.8 + i * 1.2;
            result.add(new HotelItem(name, address, stars, distanceKm));
        }
        return deduplicateAndSort(result, preference, priceRange);
    }

    private List<HotelItem> deduplicateAndSort(List<HotelItem> hotels, HotelPreference preference, PriceRange priceRange) {
        LinkedHashMap<String, HotelItem> dedup = new LinkedHashMap<>();
        for (HotelItem item : hotels) {
            String key = (item.name() + "|" + item.address()).toLowerCase(Locale.ROOT);
            dedup.putIfAbsent(key, item);
        }
        List<HotelItem> result = new ArrayList<>(dedup.values());
        Comparator<HotelItem> baseComparator = preference == HotelPreference.QUIET_PRIORITY
                ? Comparator.comparingDouble(this::quietScore).thenComparingDouble(HotelItem::distanceKm)
                : Comparator.comparingDouble(HotelItem::distanceKm);
        if (priceRange != null) {
            result.sort(Comparator
                    .comparingInt((HotelItem item) -> Math.abs(estimatePriceMidpoint(item) - priceRange.midpoint()))
                    .thenComparing(baseComparator));
        } else {
            result.sort(baseComparator);
        }
        return result;
    }

    private double quietScore(HotelItem item) {
        if (item == null) {
            return Double.MAX_VALUE;
        }
        double score = 0.0;
        if (isBusyAreaLike(item.name()) || isBusyAreaLike(item.address())) {
            score += 2.0;
        }
        double distance = item.distanceKm();
        if (distance < 1.0) {
            score += 1.5;
        } else if (distance < 2.0) {
            score += 1.0;
        } else if (distance <= 6.0) {
            score += 0.0;
        } else if (distance <= 12.0) {
            score += 0.6;
        } else {
            score += 1.5;
        }
        return score;
    }

    private boolean isBusyAreaLike(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String hint : BUSY_AREA_HINTS) {
            if (normalized.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private PriceRange resolvePriceRange(String question, String requestedPriceRange) {
        PriceRange direct = parsePriceRange(requestedPriceRange);
        if (direct != null) {
            return direct;
        }
        String structured = extractStructuredValue(question, "hotel_price_range", "hotelpricerange", "price_range");
        PriceRange fromStructured = parsePriceRange(structured);
        if (fromStructured != null) {
            return fromStructured;
        }
        return parsePriceRange(extractLabeledValue(question, "酒店期望价格", "酒店价格", "房价"));
    }

    private PriceRange parsePriceRange(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.trim();
        if ("不限".equals(normalized) || "unlimited".equalsIgnoreCase(normalized)) {
            return null;
        }
        Matcher matcher = PRICE_RANGE_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        try {
            int min = Integer.parseInt(matcher.group(1));
            int max = Integer.parseInt(matcher.group(2));
            if (min <= 0 || max <= 0) {
                return null;
            }
            if (min > max) {
                int tmp = min;
                min = max;
                max = tmp;
            }
            return new PriceRange(min, max);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int estimatePriceMidpoint(HotelItem item) {
        String text = ((item == null ? "" : item.name()) + " " + (item == null ? "" : item.address())).toLowerCase(Locale.ROOT);
        int stars = parseStarLevel(item == null ? null : item.stars());
        if (stars >= 5 || text.contains("resort") || text.contains("luxury") || text.contains("international") || text.contains("希尔顿") || text.contains("万豪") || text.contains("洲际")) {
            return 480;
        }
        if (stars == 4 || text.contains("holiday inn") || text.contains("hyatt") || text.contains("酒店")) {
            return 330;
        }
        if (text.contains("hostel") || text.contains("inn") || text.contains("快捷") || text.contains("旅舍") || text.contains("青年")) {
            return 190;
        }
        return 270;
    }

    private int parseStarLevel(String stars) {
        if (stars == null || stars.isBlank()) {
            return -1;
        }
        Matcher matcher = Pattern.compile("(\\d)").matcher(stars);
        if (!matcher.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (Exception ignored) {
            return -1;
        }
    }

    private String estimatePriceLabel(HotelItem item) {
        int mid = estimatePriceMidpoint(item);
        int min = Math.max(80, mid - 70);
        int max = mid + 80;
        return "¥" + min + "-" + max + "/晚(估)";
    }

    private String buildMapSearchLink(String city, String hotelName) {
        String query = (fallback(city, "").trim() + " " + fallback(hotelName, "hotel").trim()).trim();
        return UriComponentsBuilder.fromUriString(OSM_MAP_SEARCH_URL)
                .queryParam("query", query)
                .toUriString();
    }

    private String buildBookingLink(String hotelName, String city, DateWindow dateWindow, PriceRange priceRange) {
        String keyword = city + " " + fallback(hotelName, "酒店");
        if (priceRange != null) {
            keyword = keyword + " " + priceRange.text() + " CNY";
        }
        return UriComponentsBuilder.fromUriString(BOOKING_SEARCH_URL)
                .queryParam("ss", keyword)
                .queryParam("checkin", dateWindow.checkIn().format(DATE_FORMATTER))
                .queryParam("checkout", dateWindow.checkOut().format(DATE_FORMATTER))
                .queryParam("group_adults", 2)
                .queryParam("no_rooms", 1)
                .toUriString();
    }


    private String buildCtripLink(String hotelName, String city, DateWindow dateWindow, PriceRange priceRange) {
        String normalizedCity = TravelGeoUtils.normalizeCityToken(city);
        String cityName = normalizedCity.isBlank() ? city : normalizedCity;
        String keyword = cityName + " " + fallback(hotelName, "\u9152\u5e97");
        if (priceRange != null) {
            keyword = keyword + " " + priceRange.text() + " CNY";
        }
        return UriComponentsBuilder.fromUriString(CTRIP_SEARCH_URL)
                .queryParam("cityName", cityName)
                .queryParam("checkin", dateWindow.checkIn().format(DATE_FORMATTER))
                .queryParam("checkout", dateWindow.checkOut().format(DATE_FORMATTER))
                .queryParam("keyword", keyword)
                .toUriString();
    }

    private BookingTarget resolveBookingTarget(String city, String hotelName, DateWindow dateWindow, PriceRange priceRange) {
        String normalizedCity = TravelGeoUtils.normalizeCityToken(city);
        if (isDomesticDestination(normalizedCity)) {
            return new BookingTarget("\u643a\u7a0b", buildCtripLink(hotelName, normalizedCity, dateWindow, priceRange));
        }
        return new BookingTarget("Booking", buildBookingLink(hotelName, normalizedCity, dateWindow, priceRange));
    }

    private boolean isDomesticDestination(String city) {
        if (city == null || city.isBlank()) {
            return false;
        }
        String normalized = city.trim().toLowerCase(Locale.ROOT);
        if (DOMESTIC_CITY_HINTS.contains(normalized)) {
            return true;
        }
        if (normalized.contains("china") || normalized.contains("\u4e2d\u56fd")) {
            return true;
        }
        return normalized.chars().anyMatch(ch -> ch > 127);
    }
    private List<String> expandCityCandidates(String city) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (city != null && !city.isBlank()) {
            String cleaned = cleanupCity(city);
            set.add(cleaned);
            set.add(cleaned.toLowerCase(Locale.ROOT));
            set.add(cleaned + "市");
            set.add(city.trim());
            set.add(city.trim().toLowerCase(Locale.ROOT));
        }
        return new ArrayList<>(set);
    }

    private String resolveAddress(Map<String, String> tags) {
        String full = fallback(tags.get("addr:full"), "");
        if (!full.isBlank()) {
            return full;
        }
        String city = fallback(tags.get("addr:city"), "");
        String district = fallback(tags.get("addr:district"), "");
        String street = fallback(tags.get("addr:street"), "");
        String houseNo = fallback(tags.get("addr:housenumber"), "");
        String composed = (city + district + street + houseNo).trim();
        return composed.isBlank() ? "地址未标注" : composed;
    }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    private boolean containsStopWord(String token) {
        for (String stopWord : CITY_STOP_WORDS) {
            if (token.contains(stopWord)) {
                return true;
            }
        }
        return false;
    }

    private String cleanupCity(String city) {
        if (city == null) {
            return "";
        }
        return city.trim()
                .replace('（', ' ')
                .replace('）', ' ')
                .replace('(', ' ')
                .replace(')', ' ')
                .replace("目的地", "")
                .replace("目的地或需求", "")
                .replace("城市", "")
                .replace("酒店", "")
                .replace("住宿", "")
                .replace("旅行", "")
                .replace("旅游", "")
                .replace("行程", "")
                .replace("方案", "")
                .replace("省", "")
                .replace("市", "")
                .replace("自治区", "")
                .replace("特别行政区", "")
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

    private String formatKm(double value) {
        return String.format(Locale.ROOT, "%.1fkm", value);
    }

    private String fallback(String text, String defaultValue) {
        return text == null || text.isBlank() ? defaultValue : text;
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

    private enum HotelPreference {
        BUSINESS_DISTRICT("\u5546\u5708\u9644\u8fd1"),
        QUIET_PRIORITY("\u5b89\u9759\u4f18\u5148"),
        BOOKED("\u5df2\u9884\u5b9a");

        private final String displayLabel;

        HotelPreference(String displayLabel) {
            this.displayLabel = displayLabel;
        }

        public String displayLabel() {
            return displayLabel;
        }

        static HotelPreference fromText(String text) {
            if (text == null || text.isBlank()) {
                return BUSINESS_DISTRICT;
            }
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            if (normalized.contains("\u5df2\u9884\u5b9a") || normalized.contains("\u5df2\u9884\u8ba2") || normalized.contains("booked")) {
                return BOOKED;
            }
            if (normalized.contains("\u5b89\u9759") || normalized.contains("quiet")) {
                return QUIET_PRIORITY;
            }
            return BUSINESS_DISTRICT;
        }
    }
    public record HotelLookupResult(boolean success, String summary) {
        public static HotelLookupResult success(String summary) {
            return new HotelLookupResult(true, summary == null ? "" : summary);
        }

        public static HotelLookupResult failed(String summary) {
            return new HotelLookupResult(false, summary == null ? "" : summary);
        }
    }

    private record GeoPoint(String displayName, double latitude, double longitude) {
    }

    private record HotelItem(String name, String address, String stars, double distanceKm) {
    }

    private record DateWindow(LocalDate checkIn, LocalDate checkOut) {
    }

    private record HotelQueryResult(List<HotelItem> hotels, String source) {
    }

    private record PriceRange(int min, int max) {
        int midpoint() {
            return (min + max) / 2;
        }

        String text() {
            return min + "-" + max;
        }
    }

    private record BookingTarget(String platform, String url) {
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
    private static class OverpassResponse {
        public List<OverpassElement> elements;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OverpassElement {
        public Double lat;
        public Double lon;
        public OverpassCenter center;
        public Map<String, String> tags;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OverpassCenter {
        public Double lat;
        public Double lon;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class NominatimItem {
        public String display_name;
        public String name;
        public String lat;
        public String lon;
    }
}

