package org.fintech.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "outbox")
public class OutboxProperties {

    private boolean enabled = false;
    private String tableName;
    private String decisionQueueUrl;
    private String decisionQueueName;
    private String region;
    private String endpointOverride;
    private String accessKey;
    private String secretKey;
    private long pollIntervalMillis = 1000;
    private int batchSize = 10;
    private int maxPublishAttempts = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getDecisionQueueUrl() {
        return decisionQueueUrl;
    }

    public void setDecisionQueueUrl(String decisionQueueUrl) {
        this.decisionQueueUrl = decisionQueueUrl;
    }

    public String getDecisionQueueName() {
        return decisionQueueName;
    }

    public void setDecisionQueueName(String decisionQueueName) {
        this.decisionQueueName = decisionQueueName;
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

    public long getPollIntervalMillis() {
        return pollIntervalMillis;
    }

    public void setPollIntervalMillis(long pollIntervalMillis) {
        this.pollIntervalMillis = pollIntervalMillis;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxPublishAttempts() {
        return maxPublishAttempts;
    }

    public void setMaxPublishAttempts(int maxPublishAttempts) {
        this.maxPublishAttempts = maxPublishAttempts;
    }
}
