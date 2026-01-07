package org.fintech.sqs;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sqs")
public class SqsProperties {

    private boolean enabled = false;
    private String queueUrl;
    private String region;
    private String endpointOverride;
    private String accessKey;
    private String secretKey;
    private int maxMessages = 10;
    private int waitTimeSeconds = 10;
    private int visibilityTimeoutSeconds = 0;
    private int processingThreads = 4;
    private int processingQueueCapacity = 1000;
    private int maxInFlight = 0;
    private long pollerBackoffMillis = 200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getQueueUrl() {
        return queueUrl;
    }

    public void setQueueUrl(String queueUrl) {
        this.queueUrl = queueUrl;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpointOverride() {
        return endpointOverride;
    }

    public void setEndpointOverride(String endpointOverride) {
        this.endpointOverride = endpointOverride;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public int getMaxMessages() {
        return maxMessages;
    }

    public void setMaxMessages(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    public int getWaitTimeSeconds() {
        return waitTimeSeconds;
    }

    public void setWaitTimeSeconds(int waitTimeSeconds) {
        this.waitTimeSeconds = waitTimeSeconds;
    }

    public int getVisibilityTimeoutSeconds() {
        return visibilityTimeoutSeconds;
    }

    public void setVisibilityTimeoutSeconds(int visibilityTimeoutSeconds) {
        this.visibilityTimeoutSeconds = visibilityTimeoutSeconds;
    }

    public int getProcessingThreads() {
        return processingThreads;
    }

    public void setProcessingThreads(int processingThreads) {
        this.processingThreads = processingThreads;
    }

    public int getProcessingQueueCapacity() {
        return processingQueueCapacity;
    }

    public void setProcessingQueueCapacity(int processingQueueCapacity) {
        this.processingQueueCapacity = processingQueueCapacity;
    }

    public int getMaxInFlight() {
        return maxInFlight;
    }

    public void setMaxInFlight(int maxInFlight) {
        this.maxInFlight = maxInFlight;
    }

    public long getPollerBackoffMillis() {
        return pollerBackoffMillis;
    }

    public void setPollerBackoffMillis(long pollerBackoffMillis) {
        this.pollerBackoffMillis = pollerBackoffMillis;
    }
}
