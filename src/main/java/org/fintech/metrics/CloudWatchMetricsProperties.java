package org.fintech.metrics;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "management.metrics.export.cloudwatch")
public class CloudWatchMetricsProperties {

    private boolean enabled = false;
    private String namespace;
    private Duration step = Duration.ofMinutes(1);
    private Integer batchSize;
    private Integer numThreads;
    private Duration connectTimeout;
    private Duration readTimeout;
    private String region;
    private String endpoint;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public Duration getStep() {
        return step;
    }

    public void setStep(Duration step) {
        this.step = step;
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
    }

    public Integer getNumThreads() {
        return numThreads;
    }

    public void setNumThreads(Integer numThreads) {
        this.numThreads = numThreads;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    String get(String key) {
        if (key == null) {
            return null;
        }
        String normalized = normalizeKey(key);
        switch (normalized) {
            case "enabled":
                return Boolean.toString(enabled);
            case "namespace":
                return namespace;
            case "step":
                return formatDuration(step);
            case "batchSize":
                return formatInteger(batchSize);
            case "numThreads":
                return formatInteger(numThreads);
            case "connectTimeout":
                return formatDuration(connectTimeout);
            case "readTimeout":
                return formatDuration(readTimeout);
            default:
                return null;
        }
    }

    private static String normalizeKey(String key) {
        if (key.startsWith("cloudwatch.")) {
            return key.substring("cloudwatch.".length());
        }
        return key;
    }

    private static String formatDuration(Duration duration) {
        return duration == null ? null : duration.toString();
    }

    private static String formatInteger(Integer value) {
        return value == null ? null : value.toString();
    }
}
