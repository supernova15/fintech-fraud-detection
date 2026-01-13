package org.fintech.outbox;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(prefix = "outbox", name = "enabled", havingValue = "true")
public class OutboxRepository {

    private final DynamoDbTable<OutboxRecord> table;
    private final DynamoDbIndex<OutboxRecord> statusIndex;
    private static final Logger log = LoggerFactory.getLogger(OutboxRepository.class);

    public OutboxRepository(DynamoDbEnhancedClient enhancedClient, OutboxProperties properties) {
        if (!StringUtils.hasText(properties.getTableName())) {
            throw new IllegalStateException("outbox.table-name must be set when outbox.enabled=true");
        }
        this.table = enhancedClient.table(properties.getTableName(), TableSchema.fromBean(OutboxRecord.class));
        this.statusIndex = table.index("status-index");
    }

    public boolean putIfAbsent(OutboxRecord record) {
        try {
            table.putItem(PutItemEnhancedRequest.builder(OutboxRecord.class)
                .item(record)
                .conditionExpression(Expression.builder()
                    .expression("attribute_not_exists(outbox_id)")
                    .build())
                .build());
            return true;
        } catch (ConditionalCheckFailedException ex) {
            log.info("Claim failed for outbox_id={}, transaction_id={}", record.getOutboxId(), record.getTransactionId());
            return false;
        }
    }

    public List<OutboxRecord> fetchPending(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        log.info("Fetching pending records, now={}, limit={}", now, limit);
        Expression filterExpression = Expression.builder()
            .expression("attribute_not_exists(next_attempt_at) OR next_attempt_at <= :now")
            .expressionValues(
                java.util.Map.of(":now", AttributeValue.builder().n(Long.toString(now)).build())
            )
            .build();
        QueryConditional conditional = QueryConditional.keyEqualTo(Key.builder()
            .partitionValue(OutboxStatus.PENDING.name())
            .build());
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
            .queryConditional(conditional)
            .limit(limit)
            .filterExpression(filterExpression)
            .scanIndexForward(true)
            .build();

        List<OutboxRecord> records = new ArrayList<>();
        statusIndex.query(request).stream().forEach(page -> {
            log.info("Query page with {} items", page.items().size());
            page.items().forEach(item -> log.info("Record: outboxId={}, status={}, nextAttemptAt={}, createdAt={}",
                item.getOutboxId(), item.getStatus(), item.getNextAttemptAt(), item.getCreatedAt()));
            records.addAll(page.items());
        });
        log.info("Fetched {} pending records", records.size());
        if (records.size() > limit) {
            return records.subList(0, limit);
        }
        return records;
    }

    public void update(OutboxRecord record) {
        table.updateItem(record);
    }

    public boolean claimForPublish(OutboxRecord record, long claimLeaseMillis) {
        long now = System.currentTimeMillis();
        long expectedUpdatedAt = record.getUpdatedAt();
        log.info("Claim attempt: outboxId={}, expectedUpdatedAt={}, now={}, claimLeaseMillis={}",
            record.getOutboxId(), expectedUpdatedAt, now, claimLeaseMillis);
        record.setUpdatedAt(now);
        record.setNextAttemptAt(now + Math.max(0, claimLeaseMillis));

        Map<String, String> attributeNames = new HashMap<>();
        attributeNames.put("#status", "status");
        attributeNames.put("#updated_at", "updated_at");

        Expression condition = Expression.builder()
            .expression("#status = :pending AND #updated_at = :expectedUpdatedAt")
            .expressionValues(Map.of(
                ":pending", AttributeValue.builder().s(OutboxStatus.PENDING.name()).build(),
                ":expectedUpdatedAt", AttributeValue.builder().n(Long.toString(expectedUpdatedAt)).build()
            ))
            .expressionNames(attributeNames)
            .build();

        log.info("Claim condition: status = :pending AND updated_at = :expectedUpdatedAt, :expectedUpdatedAt={}", expectedUpdatedAt);
        try {
            table.updateItem(UpdateItemEnhancedRequest.builder(OutboxRecord.class)
                .item(record)
                .conditionExpression(condition)
                .build());
            log.info("Claim successful for outbox_id={}, transaction_id={}", record.getOutboxId(), record.getTransactionId());
            return true;
        } catch (ConditionalCheckFailedException ex) {
            log.info("Claim failed for outbox_id={}, transaction_id={}", record.getOutboxId(), record.getTransactionId());
            return false;
        } catch (Exception ex) {
            log.error("Unexpected error during claim for outbox_id={}, transaction_id={}", record.getOutboxId(), record.getTransactionId(), ex);
            throw ex;
        }
    }
}
