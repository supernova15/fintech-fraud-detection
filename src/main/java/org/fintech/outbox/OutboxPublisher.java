package org.fintech.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final int workerCount;
    private final ExecutorService pollerExecutor;
    private final ExecutorService workerExecutor;
    private final Counter publishSuccess;
    private final Counter publishFailed;
    private final Counter publishDead;
    private final Timer publishLatency;
    private volatile boolean running = false;

    public OutboxPublisher(
        OutboxRepository repository,
        OutboxProperties properties,
        SqsClient sqsClient,
        MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.properties = properties;
        this.sqsClient = sqsClient;
        this.workerCount = Math.max(1, properties.getPublishWorkers());
        this.pollerExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("outbox-publisher-poller");
            return thread;
        });
        AtomicInteger workerIndex = new AtomicInteger(1);
        this.workerExecutor = Executors.newFixedThreadPool(workerCount, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("outbox-publisher-worker-" + workerIndex.getAndIncrement());
            return thread;
        });
        this.publishSuccess = meterRegistry.counter("outbox.publish.success");
        this.publishFailed = meterRegistry.counter("outbox.publish.failed");
        this.publishDead = meterRegistry.counter("outbox.publish.dead");
        this.publishLatency = meterRegistry.timer("outbox.publish.latency");

        if (!StringUtils.hasText(properties.getDecisionQueueUrl())) {
            throw new IllegalStateException("outbox.decision-queue-url must be set when outbox.enabled=true");
        }
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        pollerExecutor.submit(this::publishLoop);
        log.info("event=outbox_publisher_started table={} decision_queue_url={} workers={}",
            properties.getTableName(),
            properties.getDecisionQueueUrl(),
            workerCount);
    }

    @Override
    public void stop() {
        running = false;
        pollerExecutor.shutdownNow();
        workerExecutor.shutdownNow();
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
                if (!running) {
                    return;
                }
                try {
                    workerExecutor.submit(() -> publishRecord(record));
                } catch (RejectedExecutionException ex) {
                    log.debug("event=outbox_publish_rejected outbox_id={} transaction_id={}",
                        record.getOutboxId(),
                        record.getTransactionId());
                }
            }
        }
    }

    private void publishRecord(OutboxRecord record) {
        if (!repository.claimForPublish(record, properties.getPublishClaimLeaseMillis())) {
            log.debug("event=outbox_publish_skipped outbox_id={} transaction_id={}",
                record.getOutboxId(),
                record.getTransactionId());
            return;
        }
        long start = System.nanoTime();
        int attempts = record.getAttempts();
        try {
            sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(properties.getDecisionQueueUrl())
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
            long now = Instant.now().toEpochMilli();
            record.setUpdatedAt(now);
            record.setNextAttemptAt(now + computeBackoffMillis(attempts));
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
        } finally {
            publishLatency.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
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

    private long computeBackoffMillis(int attempts) {
        long baseBackoff = Math.max(0, properties.getPublishBackoffMillis());
        return baseBackoff * Math.max(1, attempts);
    }
}
