package com.example.demo.rag.service.agent;

import com.example.demo.rag.config.WebSearchProperties;
import com.example.demo.rag.dto.RagReferenceItem;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class WebSearchFallbackService {

    private static final String PROVIDER_BAIDU = "baidu";
    private static final String PROVIDER_DUCKDUCKGO = "duckduckgo";
    private static final String DEFAULT_BAIDU_URL = "https://www.baidu.com/s";
    private static final String DEFAULT_DUCK_URL = "https://api.duckduckgo.com/";
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private final WebSearchProperties properties;
    private final RestTemplate restTemplate;

    public WebSearchFallbackService(WebSearchProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())))
                .readTimeout(Duration.ofSeconds(Math.max(1, properties.getReadTimeoutSeconds())))
                .build();
    }

    public WebSearchResult search(String question) {
        if (!properties.isEnabled()) {
            return WebSearchResult.failed("联网搜索已在系统配置中关闭。");
        }
        String query = normalizeQuestion(question);
        if (query.isBlank()) {
            return WebSearchResult.failed("联网搜索未触发：问题为空。");
        }

        int limit = Math.max(1, properties.getLimit());
        String primaryProvider = normalizeProvider(properties.getProvider());
        List<WebHit> hits = searchByProvider(primaryProvider, fallback(properties.getBaseUrl(), ""), query, limit);
        String usedProvider = providerLabel(primaryProvider);

        if (hits.isEmpty()) {
            String fallbackProvider = normalizeProvider(properties.getFallbackProvider());
            if (!fallbackProvider.equals(primaryProvider)) {
                hits = searchByProvider(fallbackProvider, fallback(properties.getFallbackBaseUrl(), ""), query, limit);
                usedProvider = providerLabel(fallbackProvider);
            }
        }

        if (hits.isEmpty()) {
            return WebSearchResult.failed("联网搜索未命中可用结果，请稍后重试或更换关键词。");
        }

        int keep = Math.min(limit, hits.size());
        List<RagReferenceItem> references = new ArrayList<>(keep);
        for (int i = 0; i < keep; i++) {
            WebHit hit = hits.get(i);
            double score = Math.max(0.45, 0.86 - i * 0.06);
            long pseudoId = -9_000_000L - i;
            references.add(new RagReferenceItem(
                    pseudoId,
                    pseudoId,
                    fallback(hit.title(), "联网结果" + (i + 1)),
                    "网页搜索(" + usedProvider + ")",
                    fallback(hit.url(), ""),
                    0.0,
                    0.0,
                    score,
                    score,
                    fallback(hit.snippet(), "")
            ));
        }

        return WebSearchResult.success("联网搜索（" + usedProvider + "）补充了 " + references.size() + " 条参考信息。", references);
    }

    private List<WebHit> searchByProvider(String provider, String baseUrl, String query, int limit) {
        return switch (provider) {
            case PROVIDER_BAIDU -> searchByBaidu(baseUrl, query, limit);
            case PROVIDER_DUCKDUCKGO -> searchByDuckDuckGo(baseUrl, query, limit);
            default -> List.of();
        };
    }

    private List<WebHit> searchByBaidu(String baseUrl, String query, int limit) {
        String effectiveBaseUrl = fallback(baseUrl, DEFAULT_BAIDU_URL);
        try {
            String url = UriComponentsBuilder.fromUriString(effectiveBaseUrl)
                    .queryParam("wd", query)
                    .queryParam("ie", "utf-8")
                    .queryParam("rn", Math.max(10, limit * 3))
                    .toUriString();
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT);
            headers.set(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9");

            ResponseEntity<String> entity = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            String body = entity.getBody();
            if (body == null || body.isBlank()) {
                return List.of();
            }

            Document document = Jsoup.parse(body, effectiveBaseUrl);
            Elements blocks = document.select("div.result, div.result-op, div.c-container");
            List<WebHit> hits = new ArrayList<>();
            Set<String> dedup = new LinkedHashSet<>();
            for (Element block : blocks) {
                if (hits.size() >= limit) {
                    break;
                }
                Element titleLink = block.selectFirst("h3 a");
                if (titleLink == null) {
                    titleLink = block.selectFirst("a[href]");
                }
                if (titleLink == null) {
                    continue;
                }
                String title = cleanupText(titleLink.text());
                String href = cleanupUrl(fallback(titleLink.absUrl("href"), titleLink.attr("href")));
                if (title.isBlank()) {
                    continue;
                }
                String snippet = cleanupText(extractSnippet(block));
                String dedupKey = title + "|" + href + "|" + snippet;
                if (!dedup.add(dedupKey)) {
                    continue;
                }
                hits.add(new WebHit(title, href, snippet));
            }

            if (!hits.isEmpty()) {
                return hits;
            }

            // Fallback parser for simplified pages.
            Elements links = document.select("h3 a");
            for (Element link : links) {
                if (hits.size() >= limit) {
                    break;
                }
                String title = cleanupText(link.text());
                String href = cleanupUrl(fallback(link.absUrl("href"), link.attr("href")));
                if (title.isBlank()) {
                    continue;
                }
                String dedupKey = title + "|" + href;
                if (!dedup.add(dedupKey)) {
                    continue;
                }
                hits.add(new WebHit(title, href, ""));
            }
            return hits;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String extractSnippet(Element block) {
        if (block == null) {
            return "";
        }
        Element snippet = block.selectFirst("div.c-abstract, div.c-span-last, div.content-right_8Zs40, div.c-gap-top-small, div.c-font-normal");
        if (snippet != null) {
            return snippet.text();
        }
        Element paragraph = block.selectFirst("p");
        return paragraph == null ? "" : paragraph.text();
    }

    private List<WebHit> searchByDuckDuckGo(String baseUrl, String query, int limit) {
        String effectiveBaseUrl = fallback(baseUrl, DEFAULT_DUCK_URL);
        try {
            String url = UriComponentsBuilder.fromUriString(effectiveBaseUrl)
                    .queryParam("q", query)
                    .queryParam("format", "json")
                    .queryParam("no_redirect", "1")
                    .queryParam("no_html", "1")
                    .queryParam("skip_disambig", "1")
                    .toUriString();

            DuckSearchResponse response = restTemplate.getForObject(url, DuckSearchResponse.class);
            if (response == null) {
                return List.of();
            }
            List<WebHit> hits = extractDuckHits(response);
            if (hits.size() <= limit) {
                return hits;
            }
            return new ArrayList<>(hits.subList(0, limit));
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<WebHit> extractDuckHits(DuckSearchResponse response) {
        List<WebHit> hits = new ArrayList<>();
        Set<String> dedup = new LinkedHashSet<>();

        if (response.AbstractText != null && !response.AbstractText.isBlank()) {
            String url = fallback(response.AbstractURL, "");
            String key = url + "|" + response.AbstractText;
            if (dedup.add(key)) {
                hits.add(new WebHit(
                        fallback(response.Heading, "联网摘要"),
                        url,
                        cleanupText(response.AbstractText)
                ));
            }
        }

        if (response.RelatedTopics != null) {
            for (DuckTopic topic : response.RelatedTopics) {
                collectDuckTopic(topic, hits, dedup);
            }
        }
        return hits;
    }

    private void collectDuckTopic(DuckTopic topic, List<WebHit> hits, Set<String> dedup) {
        if (topic == null) {
            return;
        }
        if (topic.Topics != null && !topic.Topics.isEmpty()) {
            for (DuckTopic child : topic.Topics) {
                collectDuckTopic(child, hits, dedup);
            }
            return;
        }
        String text = cleanupText(fallback(topic.Text, ""));
        String url = cleanupUrl(fallback(topic.FirstURL, ""));
        if (text.isBlank()) {
            return;
        }
        String dedupKey = url + "|" + text;
        if (!dedup.add(dedupKey)) {
            return;
        }
        String title = text;
        int dash = text.indexOf(" - ");
        if (dash > 0) {
            title = text.substring(0, dash).trim();
        }
        hits.add(new WebHit(title, url, text));
    }

    private String normalizeProvider(String provider) {
        String normalized = fallback(provider, "").trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return PROVIDER_BAIDU;
        }
        if ("ddg".equals(normalized)) {
            return PROVIDER_DUCKDUCKGO;
        }
        return normalized;
    }

    private String providerLabel(String provider) {
        return switch (provider) {
            case PROVIDER_BAIDU -> "Baidu";
            case PROVIDER_DUCKDUCKGO -> "DuckDuckGo";
            default -> provider;
        };
    }

    private String normalizeQuestion(String question) {
        if (question == null) {
            return "";
        }
        String value = question.trim().replace('\n', ' ');
        return value.replaceAll("\\s{2,}", " ").trim();
    }

    private String cleanupText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace('\u00A0', ' ')
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private String cleanupUrl(String url) {
        if (url == null) {
            return "";
        }
        return url.trim();
    }

    private String fallback(String text, String defaultValue) {
        return text == null || text.isBlank() ? defaultValue : text;
    }

    public record WebSearchResult(boolean success, String summary, List<RagReferenceItem> references) {
        public static WebSearchResult success(String summary, List<RagReferenceItem> references) {
            return new WebSearchResult(true, summary == null ? "" : summary, references == null ? List.of() : List.copyOf(references));
        }

        public static WebSearchResult failed(String summary) {
            return new WebSearchResult(false, summary == null ? "" : summary, List.of());
        }
    }

    private record WebHit(String title, String url, String snippet) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DuckSearchResponse {
        public String Heading;
        public String AbstractText;
        public String AbstractURL;
        public List<DuckTopic> RelatedTopics = List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DuckTopic {
        public String Text;
        public String FirstURL;
        public List<DuckTopic> Topics;
    }
}
