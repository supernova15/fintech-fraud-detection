package org.fintech.outbox;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

@DynamoDbBean
public class OutboxRecord {

    private String outboxId;
    private String transactionId;
    private String messageId;
    private String status;
    private String payloadBase64;
    private String decision;
    private String reason;
    private double riskScore;
    private long createdAt;
    private long updatedAt;
    private int attempts;
    private String lastError;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("outbox_id")
    public String getOutboxId() {
        return outboxId;
    }

    public void setOutboxId(String outboxId) {
        this.outboxId = outboxId;
    }

    @DynamoDbAttribute("transaction_id")
    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    @DynamoDbAttribute("message_id")
    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "status-index")
    @DynamoDbAttribute("status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @DynamoDbSecondarySortKey(indexNames = "status-index")
    @DynamoDbAttribute("created_at")
    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDbAttribute("updated_at")
    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    @DynamoDbAttribute("attempts")
    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    @DynamoDbAttribute("payload_base64")
    public String getPayloadBase64() {
        return payloadBase64;
    }

    public void setPayloadBase64(String payloadBase64) {
        this.payloadBase64 = payloadBase64;
    }

    @DynamoDbAttribute("decision")
    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    @DynamoDbAttribute("reason")
    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @DynamoDbAttribute("risk_score")
    public double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(double riskScore) {
        this.riskScore = riskScore;
    }

    @DynamoDbAttribute("last_error")
    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
