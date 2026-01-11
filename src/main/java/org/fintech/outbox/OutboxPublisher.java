package org.fintech.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.fintech.sqs.SqsQueueUrlResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Component
@ConditionalOnProperty(prefix = "outbox", name = "enabled", havingValue = "true")
public class OutboxPublisher implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository repository;
    private final OutboxProperties properties;
    private final SqsClient sqsClient;
    private final SqsQueueUrlResolver queueUrlResolver;
    private final ExecutorService executor;
    private final Counter publishSuccess;
    private final Counter publishFailed;
    private final Counter publishDead;
    private volatile String decisionQueueUrl;
    private volatile boolean running = false;

    public OutboxPublisher(
        OutboxRepository repository,
        OutboxProperties properties,
        SqsClient sqsClient,
        SqsQueueUrlResolver queueUrlResolver,
        MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.properties = properties;
        this.sqsClient = sqsClient;
        this.queueUrlResolver = queueUrlResolver;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("outbox-publisher");
            return thread;
        });
        this.publishSuccess = meterRegistry.counter("outbox.publish.success");
        this.publishFailed = meterRegistry.counter("outbox.publish.failed");
        this.publishDead = meterRegistry.counter("outbox.publish.dead");

        if (!StringUtils.hasText(properties.getDecisionQueueUrl())
            && !StringUtils.hasText(properties.getDecisionQueueName())) {
            throw new IllegalStateException(
                "outbox.decision-queue-name or outbox.decision-queue-url must be set when outbox.enabled=true"
            );
        }
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        this.decisionQueueUrl = queueUrlResolver.resolveQueueUrl(
            properties.getDecisionQueueName(),
            properties.getDecisionQueueUrl(),
            "outbox.decision-queue-name",
            "outbox.decision-queue-url"
        );
        running = true;
        executor.submit(this::publishLoop);
        log.info("event=outbox_publisher_started table={} decision_queue_url={}",
            properties.getTableName(),
            decisionQueueUrl);
    }

    @Override
    public void stop() {
        running = false;
        executor.shutdownNow();
        log.info("event=outbox_publisher_stopped table={}", properties.getTableName());
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 10;
    }

    private void publishLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            List<OutboxRecord> records = repository.fetchPending(properties.getBatchSize());
            if (records.isEmpty()) {
                sleepInterval();
                continue;
            }
            for (OutboxRecord record : records) {
                publishRecord(record);
            }
        }
    }

    private void publishRecord(OutboxRecord record) {
        int attempts = record.getAttempts();
        try {
            sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(decisionQueueUrl)
                .messageBody(record.getPayloadBase64())
                .build());
            record.setStatus(OutboxStatus.PUBLISHED.name());
            record.setUpdatedAt(Instant.now().toEpochMilli());
            repository.update(record);
            publishSuccess.increment();
            log.info(
                "event=outbox_publish_success outbox_id={} transaction_id={} attempts={}",
                record.getOutboxId(),
                record.getTransactionId(),
                attempts
            );
        } catch (Exception ex) {
            attempts += 1;
            record.setAttempts(attempts);
            record.setUpdatedAt(Instant.now().toEpochMilli());
            record.setLastError(ex.getMessage());
            if (attempts >= properties.getMaxPublishAttempts()) {
                record.setStatus(OutboxStatus.FAILED.name());
                publishDead.increment();
            }
            repository.update(record);
            publishFailed.increment();
            log.warn(
                "event=outbox_publish_failed outbox_id={} transaction_id={} attempts={}",
                record.getOutboxId(),
                record.getTransactionId(),
                attempts,
                ex
            );
        }
    }

    private void sleepInterval() {
        try {
            long interval = Math.max(0, properties.getPollIntervalMillis());
            TimeUnit.MILLISECONDS.sleep(interval);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
