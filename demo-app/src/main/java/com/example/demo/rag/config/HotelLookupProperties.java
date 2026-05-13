package com.example.demo.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.hotel")
/**
 * HotelLookupProperties配置类。
 * 该类用于管理酒店查询外部接口配置：
 * 1) 是否启用；
 * 2) 服务商标识与接口地址；
 * 3) 查询半径、返回条数上限；
 * 4) 连接与读取超时设置。
 * 通过集中配置可在不改代码的前提下切换服务地址或临时关闭酒店查询。
 */
public class HotelLookupProperties {

    private boolean enabled = true;
    private String provider = "overpass";
    private String geocodeUrl = "https://geocoding-api.open-meteo.com/v1/search";
    private String overpassUrl = "https://overpass-api.de/api/interpreter";
    private int radiusMeters = 6000;
    private int limit = 6;
    private int connectTimeoutSeconds = 3;
    private int readTimeoutSeconds = 5;

    /**
     * 获取是否启用酒店查询。
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用酒店查询。
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取酒店服务商标识。
     */
    public String getProvider() {
        return provider;
    }

    /**
     * 设置酒店服务商标识。
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    /**
     * 获取地理编码接口地址。
     */
    public String getGeocodeUrl() {
        return geocodeUrl;
    }

    /**
     * 设置地理编码接口地址。
     */
    public void setGeocodeUrl(String geocodeUrl) {
        this.geocodeUrl = geocodeUrl;
    }

    /**
     * 获取酒店检索接口地址。
     */
    public String getOverpassUrl() {
        return overpassUrl;
    }

    /**
     * 设置酒店检索接口地址。
     */
    public void setOverpassUrl(String overpassUrl) {
        this.overpassUrl = overpassUrl;
    }

    /**
     * 获取酒店检索半径（米）。
     */
    public int getRadiusMeters() {
        return radiusMeters;
    }

    /**
     * 设置酒店检索半径（米）。
     */
    public void setRadiusMeters(int radiusMeters) {
        this.radiusMeters = radiusMeters;
    }

    /**
     * 获取返回条数上限。
     */
    public int getLimit() {
        return limit;
    }

    /**
     * 设置返回条数上限。
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }

    /**
     * 获取连接超时时长（秒）。
     */
    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    /**
     * 设置连接超时时长（秒）。
     */
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    /**
     * 获取读取超时时长（秒）。
     */
    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    /**
     * 设置读取超时时长（秒）。
     */
    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }
}
