package com.example.demo.rag.service;

import com.example.demo.rag.dto.MapRoutePlanResponse;
import com.example.demo.rag.dto.MapRoutePointResponse;
import com.example.demo.rag.dto.MapRouteSegmentResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class MapRouteService {

    private static final String NOMINATIM_API = "https://nominatim.openstreetmap.org/search";
    private static final String PHOTON_API = "https://photon.komoot.io/api/";
    private static final String OSRM_ROUTE_API = "https://router.project-osrm.org/route/v1";
    private static final int MAX_PATH_POINTS = 180;

    private final RestTemplate restTemplate;

    public MapRouteService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }

    public MapRoutePlanResponse planRoutes(String city, List<String> places, String travelMode) {
        List<String> normalized = normalizePlaces(places);
        if (normalized.size() < 2) {
            return new MapRoutePlanResponse(
                    fallback(city, ""),
                    resolveProfile(travelMode),
                    "地点不足，至少需要 2 个地点才能规划路线。",
                    List.of()
            );
        }

        String profile = resolveProfile(travelMode);
        List<MapRouteSegmentResponse> segments = new ArrayList<>();
        Set<String> unresolvedPlaces = new LinkedHashSet<>();
        int skippedSegments = 0;
        for (int i = 0; i < normalized.size() - 1; i++) {
            String from = normalized.get(i);
            String to = normalized.get(i + 1);
            GeoPoint fromPoint = geocode(from, city);
            GeoPoint toPoint = geocode(to, city);
            if (fromPoint == null || toPoint == null) {
                skippedSegments++;
                if (fromPoint == null) {
                    unresolvedPlaces.add(from);
                }
                if (toPoint == null) {
                    unresolvedPlaces.add(to);
                }
                continue;
            }
            MapRouteSegmentResponse segment = route(fromPoint, toPoint, profile);
            if (segment != null) {
                segments.add(segment);
            } else {
                skippedSegments++;
            }
        }

        String summary;
        if (segments.isEmpty()) {
            if (!unresolvedPlaces.isEmpty()) {
                summary = "未能查询到可用路线，以下地点暂未识别："
                        + String.join("、", unresolvedPlaces.stream().limit(5).toList())
                        + "。请在方案中使用更具体地点名（如：黄鹤楼、湖北省博物馆）。";
            } else {
                summary = "未能查询到可用路线，请稍后重试或切换更具体地点名称。";
            }
        } else {
            double totalDistance = segments.stream().mapToDouble(MapRouteSegmentResponse::distanceKm).sum();
            int totalMinutes = segments.stream().mapToInt(MapRouteSegmentResponse::durationMinutes).sum();
            summary = "已规划 " + segments.size() + " 段路线，预计总里程 "
                    + String.format(Locale.ROOT, "%.1f", totalDistance)
                    + " 公里，总耗时约 " + totalMinutes + " 分钟。";
            if (skippedSegments > 0) {
                summary += "（另有 " + skippedSegments + " 段因地点识别失败被跳过）";
            }
        }

        return new MapRoutePlanResponse(fallback(city, ""), profile, summary, segments);
    }

    private List<String> normalizePlaces(List<String> places) {
        if (places == null || places.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String place : places) {
            if (place == null) {
                continue;
            }
            String cleaned = simplifyPlaceName(place);
            if (cleaned.length() < 2 || cleaned.length() > 40) {
                continue;
            }
            set.add(cleaned);
            if (set.size() >= 8) {
                break;
            }
        }
        return new ArrayList<>(set);
    }

    private String resolveProfile(String travelMode) {
        if (travelMode == null || travelMode.isBlank()) {
            return "driving";
        }
        String normalized = travelMode.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("自驾") || normalized.contains("driving")) {
            return "driving";
        }
        if (normalized.contains("步行") || normalized.contains("walking")) {
            return "walking";
        }
        if (normalized.contains("骑行") || normalized.contains("bike") || normalized.contains("cycling")) {
            return "cycling";
        }
        return "driving";
    }

    private GeoPoint geocode(String place, String city) {
        String cityHint = normalizeCityHint(city);
        String normalizedPlace = simplifyPlaceName(place);
        if (normalizedPlace.isBlank()) {
            return null;
        }
        List<String> placeVariants = expandPlaceVariants(normalizedPlace);
        List<String> queryCandidates = new ArrayList<>();
        for (String variant : placeVariants) {
            if (!cityHint.isBlank()) {
                queryCandidates.add(cityHint + " " + variant);
            }
            queryCandidates.add(variant);
        }
        if (!normalizedPlace.equals(place)) {
            String rawPlace = fallback(place, "").trim();
            if (!rawPlace.isBlank()) {
                if (!cityHint.isBlank()) {
                    queryCandidates.add(cityHint + " " + rawPlace);
                }
                queryCandidates.add(rawPlace);
            }
        }

        Set<String> tried = new HashSet<>();
        for (String query : queryCandidates) {
            String q = fallback(query, "").trim();
            if (q.isBlank() || !tried.add(q)) {
                continue;
            }
            GeoPoint hit = geocodeByQuery(q, place);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private List<String> expandPlaceVariants(String place) {
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        String base = fallback(place, "").trim();
        if (base.isBlank()) {
            return List.of();
        }
        variants.add(base);

        String compact = base
                .replaceAll("(地铁站|火车站|高铁站|客运站)$", "")
                .replaceAll("站$", "")
                .trim();
        if (!compact.isBlank()) {
            variants.add(compact);
        }

        boolean stationLike = looksLikeStation(base);
        if (stationLike && !compact.isBlank()) {
            variants.add(compact + "站");
            variants.add(compact + "地铁站");
            variants.add(compact + "火车站");
            variants.add(compact + "高铁站");
            variants.add(compact + "铁路客运站");
        }
        if (base.endsWith("机场")) {
            variants.add(base + "T2");
            variants.add(base + "航站楼");
        }
        return new ArrayList<>(variants);
    }

    private boolean looksLikeStation(String text) {
        String value = fallback(text, "");
        return value.endsWith("站")
                || value.endsWith("机场")
                || value.endsWith("码头")
                || value.endsWith("口岸")
                || value.contains("高铁")
                || value.contains("火车")
                || value.contains("地铁")
                || value.contains("客运")
                || value.contains("虹桥")
                || value.contains("汉口")
                || value.contains("武昌")
                || value.contains("江汉路");
    }

    private GeoPoint geocodeByQuery(String query, String placeLabel) {
        GeoPoint byNominatim = geocodeByNominatim(query, placeLabel);
        if (byNominatim != null) {
            return byNominatim;
        }
        return geocodeByPhoton(query, placeLabel);
    }

    private GeoPoint geocodeByNominatim(String query, String placeLabel) {
        String url = UriComponentsBuilder.fromUriString(NOMINATIM_API)
                .queryParam("q", query)
                .queryParam("format", "jsonv2")
                .queryParam("limit", 5)
                .queryParam("addressdetails", 0)
                .queryParam("countrycodes", "cn")
                .toUriString();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "TravelAssistant/1.0 (contact: support@example.com)");
            headers.set(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9,en;q=0.8");
            ResponseEntity<NominatimItem[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    NominatimItem[].class
            );
            NominatimItem[] items = response.getBody();
            if (items == null || items.length == 0) {
                return null;
            }
            for (NominatimItem item : items) {
                if (item == null || item.lat == null || item.lon == null) {
                    continue;
                }
                double lat = parseDouble(item.lat);
                double lon = parseDouble(item.lon);
                if (Double.isNaN(lat) || Double.isNaN(lon)) {
                    continue;
                }
                return new GeoPoint(placeLabel, lat, lon);
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private GeoPoint geocodeByPhoton(String query, String placeLabel) {
        String url = UriComponentsBuilder.fromUriString(PHOTON_API)
                .queryParam("q", query)
                .queryParam("limit", 5)
                .queryParam("lang", "zh")
                .toUriString();
        try {
            PhotonResponse response = restTemplate.getForObject(url, PhotonResponse.class);
            if (response == null || response.features == null || response.features.isEmpty()) {
                return null;
            }
            for (PhotonFeature feature : response.features) {
                if (feature == null || feature.geometry == null || feature.geometry.coordinates == null) {
                    continue;
                }
                List<Double> coords = feature.geometry.coordinates;
                if (coords.size() < 2 || coords.get(0) == null || coords.get(1) == null) {
                    continue;
                }
                double lon = coords.get(0);
                double lat = coords.get(1);
                if (!Double.isFinite(lat) || !Double.isFinite(lon)) {
                    continue;
                }
                if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                    continue;
                }
                return new GeoPoint(placeLabel, lat, lon);
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private MapRouteSegmentResponse route(GeoPoint from, GeoPoint to, String profile) {
        String safeProfile = fallback(profile, "driving");
        String url = UriComponentsBuilder.fromUriString(OSRM_ROUTE_API
                        + "/" + safeProfile
                        + "/" + from.lon + "," + from.lat
                        + ";" + to.lon + "," + to.lat)
                .queryParam("overview", "full")
                .queryParam("geometries", "geojson")
                .queryParam("alternatives", "false")
                .queryParam("steps", "false")
                .toUriString();
        try {
            OsrmResponse response = restTemplate.getForObject(url, OsrmResponse.class);
            if (response == null || response.routes == null || response.routes.isEmpty()) {
                return buildFallbackSegment(from, to, safeProfile);
            }
            OsrmRoute first = response.routes.get(0);
            if (first == null) {
                return buildFallbackSegment(from, to, safeProfile);
            }
            double distanceKm = Math.max(0.1, first.distance / 1000.0);
            int durationMinutes = Math.max(1, (int) Math.round(first.duration / 60.0));
            List<MapRoutePointResponse> path = extractPath(first.geometry);
            if (path.isEmpty()) {
                path = defaultPath(from, to);
            }
            return new MapRouteSegmentResponse(
                    from.name,
                    to.name,
                    distanceKm,
                    durationMinutes,
                    buildOpenStreetMapUrl(from, to, safeProfile),
                    path
            );
        } catch (Exception ex) {
            return buildFallbackSegment(from, to, safeProfile);
        }
    }

    private List<MapRoutePointResponse> extractPath(OsrmGeometry geometry) {
        if (geometry == null || geometry.coordinates == null || geometry.coordinates.isEmpty()) {
            return List.of();
        }
        List<List<Double>> coordinates = geometry.coordinates;
        int total = coordinates.size();
        int step = Math.max(1, total / MAX_PATH_POINTS);
        List<MapRoutePointResponse> points = new ArrayList<>();
        for (int i = 0; i < total; i += step) {
            MapRoutePointResponse point = toPoint(coordinates.get(i));
            if (point != null) {
                points.add(point);
            }
        }
        if ((total - 1) % step != 0) {
            MapRoutePointResponse last = toPoint(coordinates.get(total - 1));
            if (last != null && (points.isEmpty() || !samePoint(points.get(points.size() - 1), last))) {
                points.add(last);
            }
        }
        return points;
    }

    private MapRoutePointResponse toPoint(List<Double> coordinate) {
        if (coordinate == null || coordinate.size() < 2) {
            return null;
        }
        Double lonRaw = coordinate.get(0);
        Double latRaw = coordinate.get(1);
        if (lonRaw == null || latRaw == null) {
            return null;
        }
        double lat = latRaw;
        double lon = lonRaw;
        if (!Double.isFinite(lat) || !Double.isFinite(lon)) {
            return null;
        }
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            return null;
        }
        return new MapRoutePointResponse(lat, lon);
    }

    private boolean samePoint(MapRoutePointResponse a, MapRoutePointResponse b) {
        return Double.compare(a.lat(), b.lat()) == 0 && Double.compare(a.lon(), b.lon()) == 0;
    }

    private MapRouteSegmentResponse buildFallbackSegment(GeoPoint from, GeoPoint to, String profile) {
        double distanceKm = Math.max(0.1, haversineKm(from.lat, from.lon, to.lat, to.lon));
        double speedKmPerHour = switch (profile) {
            case "walking" -> 4.5;
            case "cycling" -> 15.0;
            default -> 30.0;
        };
        int durationMinutes = Math.max(1, (int) Math.round(distanceKm / speedKmPerHour * 60.0));
        return new MapRouteSegmentResponse(
                from.name,
                to.name,
                distanceKm,
                durationMinutes,
                buildOpenStreetMapUrl(from, to, profile),
                defaultPath(from, to)
        );
    }

    private List<MapRoutePointResponse> defaultPath(GeoPoint from, GeoPoint to) {
        return List.of(
                new MapRoutePointResponse(from.lat, from.lon),
                new MapRoutePointResponse(to.lat, to.lon)
        );
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }

    private String normalizeCityHint(String city) {
        String text = fallback(city, "")
                .replaceAll("(旅游|攻略|行程|自由行|周边游|亲子游|自驾游|路线|计划|方案|\\d+天\\d+晚)", " ")
                .trim();
        if (text.isBlank()) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([\\u4e00-\\u9fa5]{2,12}(?:市|州|地区|盟)?)").matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return text.split("\\s+")[0];
    }

    private String simplifyPlaceName(String raw) {
        String text = fallback(raw, "")
                .replaceAll("`+", " ")
                .replaceAll("\\*{1,3}", " ")
                .replaceAll("_{1,3}", " ")
                .replaceAll("\\[[^\\]]*\\]\\([^)]*\\)", " ")
                .replaceAll("[（(][^）)]*[）)]", " ")
                .replaceAll("^(第?\\d+天|day\\s*\\d+|d-?\\d+)\\s*[:：-]?\\s*", "")
                .replaceAll("^(上午|中午|下午|晚上|夜间|傍晚|早上|早晨|凌晨)\\s*[:：-]?\\s*", "")
                .replaceAll("^(出发|前往|抵达|到达|游玩|打卡|入住|返回|经停|换乘)\\s*", "")
                .replaceAll("^(跨城交通|城内交通|交通接驳|酒店接驳|接驳|交通|路线|路线规划|行程路线|游玩路线|推荐路线|方案)\\s*[:：-]\\s*", "")
                .replaceAll("(地铁|公交)\\s*\\d+\\s*号?线?", " ")
                .replaceAll("步行\\s*约?\\s*\\d+\\s*(米|分钟)", " ")
                .replaceAll("约?\\s*\\d+\\s*分钟", " ")
                .replaceAll("¥\\s*\\d+|\\d+\\s*元", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        String[] segments = text.split("[，。；;|]");
        if (segments.length > 0) {
            text = fallback(segments[0], "").trim();
        }
        if (text.contains("：") || text.contains(":")) {
            String[] labelSplit = text.split("[:：]");
            text = fallback(labelSplit[labelSplit.length - 1], "").trim();
        }
        text = text.replaceAll("^[^\\u4e00-\\u9fa5A-Za-z0-9]+", "").trim();
        if (text.length() > 36) {
            text = text.substring(0, 36).trim();
        }
        return text;
    }

    private String buildOpenStreetMapUrl(GeoPoint from, GeoPoint to, String profile) {
        String engine = switch (profile) {
            case "walking" -> "fossgis_osrm_foot";
            case "cycling" -> "fossgis_osrm_bike";
            default -> "fossgis_osrm_car";
        };
        return UriComponentsBuilder.fromUriString("https://www.openstreetmap.org/directions")
                .queryParam("engine", engine)
                .queryParam("route",
                        String.format(Locale.ROOT, "%.6f,%.6f;%.6f,%.6f", from.lat, from.lon, to.lat, to.lon))
                .toUriString();
    }

    private double parseDouble(String text) {
        try {
            return Double.parseDouble(text);
        } catch (Exception ex) {
            return Double.NaN;
        }
    }

    private String fallback(String text, String defaultValue) {
        return text == null || text.isBlank() ? defaultValue : text;
    }

    private record GeoPoint(String name, double lat, double lon) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class NominatimItem {
        public String lat;
        public String lon;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PhotonResponse {
        public List<PhotonFeature> features;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PhotonFeature {
        public PhotonGeometry geometry;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PhotonGeometry {
        public List<Double> coordinates;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OsrmResponse {
        public List<OsrmRoute> routes;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OsrmRoute {
        public double distance;
        public double duration;
        public OsrmGeometry geometry;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OsrmGeometry {
        public List<List<Double>> coordinates;
    }
}
