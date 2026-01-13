package org.fintech.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.fintech.proto.v1.RiskAssessment;
import org.fintech.sqs.SqsTransactionProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(prefix = "outbox", name = "enabled", havingValue = "true")
public class OutboxWriter {
    private static final Logger log = LoggerFactory.getLogger(OutboxWriter.class);

    private final OutboxRepository repository;
    private final Counter writeCreated;
    private final Counter writeDuplicate;
    private final Counter writeFailed;
    private final Timer writeLatency;

    public OutboxWriter(OutboxRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.writeCreated = meterRegistry.counter("outbox.write.created");
        this.writeDuplicate = meterRegistry.counter("outbox.write.duplicate");
        this.writeFailed = meterRegistry.counter("outbox.write.failed");
        this.writeLatency = meterRegistry.timer("outbox.write.latency");
    }

    public OutboxWriteResult write(SqsTransactionProcessor.ProcessedTransaction processed, String messageId) {
        long start = System.nanoTime();
        String transactionId = processed.request().getTransactionId();
        String outboxId = StringUtils.hasText(transactionId) ? transactionId : messageId;
        long now = Instant.now().toEpochMilli();

        RiskAssessment assessment = RiskAssessment.newBuilder()
            .setTransactionId(transactionId)
            .setDecision(processed.result().decision())
            .setReason(processed.result().reason())
            .setRiskScore(processed.result().riskScore())
            .build();

        OutboxRecord record = new OutboxRecord();
        record.setOutboxId(outboxId);
        record.setTransactionId(transactionId);
        record.setMessageId(messageId);
        record.setStatus(OutboxStatus.PENDING.name());
        record.setPayloadBase64(Base64.getEncoder().encodeToString(assessment.toByteArray()));
        record.setDecision(processed.result().decision().name());
        record.setReason(processed.result().reason().name());
        record.setRiskScore(processed.result().riskScore());
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setNextAttemptAt(now);
        log.info("Writing outbox record, outboxId={}, nextAttemptAt={}", outboxId, now);
        record.setAttempts(0);

        try {
            boolean created = repository.putIfAbsent(record);
            if (created) {
                writeCreated.increment();
            } else {
                writeDuplicate.increment();
            }
            return new OutboxWriteResult(outboxId, created);
        } catch (Exception ex) {
            writeFailed.increment();
            throw ex;
        } finally {
            writeLatency.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }
}
