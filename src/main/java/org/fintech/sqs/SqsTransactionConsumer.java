package org.fintech.sqs;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.fintech.outbox.OutboxWriteResult;
import org.fintech.outbox.OutboxWriter;
import org.fintech.rules.RuleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

@Component
@ConditionalOnProperty(prefix = "sqs", name = "enabled", havingValue = "true")
public class SqsTransactionConsumer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SqsTransactionConsumer.class);

    private final SqsClient sqsClient;
    private final SqsProperties properties;
    private final SqsQueueUrlResolver queueUrlResolver;
    private final SqsTransactionProcessor processor;
    private final ExecutorService processingExecutor;
    private final ExecutorService pollerExecutor;
    private final ObjectProvider<OutboxWriter> outboxWriterProvider;
    private final int pollerThreads;
    private final AtomicInteger inFlight;
    private final int maxInFlight;
    private final Counter pollCount;
    private final Counter pollFailure;
    private final Timer pollLatency;
    private final Counter messagesReceived;
    private final Counter processSuccess;
    private final Counter processFailure;
    private final Timer processLatency;
    private volatile String queueUrl;
    private volatile boolean running = false;

    public SqsTransactionConsumer(
        SqsClient sqsClient,
        SqsProperties properties,
        SqsQueueUrlResolver queueUrlResolver,
        SqsTransactionProcessor processor,
        @Qualifier("sqsProcessingExecutor") ExecutorService sqsProcessingExecutor,
        @Qualifier("sqsPollerExecutor") ExecutorService sqsPollerExecutor,
        ObjectProvider<OutboxWriter> outboxWriterProvider,
        MeterRegistry meterRegistry
    ) {
        this.sqsClient = sqsClient;
        this.properties = properties;
        this.queueUrlResolver = queueUrlResolver;
        this.processor = processor;
        this.processingExecutor = sqsProcessingExecutor;
        this.pollerThreads = Math.max(1, properties.getPollerThreads());
        this.pollerExecutor = sqsPollerExecutor;
        this.outboxWriterProvider = outboxWriterProvider;
        this.inFlight = new AtomicInteger();
        this.maxInFlight = resolveMaxInFlight(properties);
        this.pollCount = meterRegistry.counter("sqs.poll.count");
        this.pollFailure = meterRegistry.counter("sqs.poll.failure");
        this.pollLatency = meterRegistry.timer("sqs.poll.latency");
        this.messagesReceived = meterRegistry.counter("sqs.messages.received");
        this.processSuccess = meterRegistry.counter("sqs.process.success");
        this.processFailure = meterRegistry.counter("sqs.process.failure");
        this.processLatency = meterRegistry.timer("sqs.process.latency");
        meterRegistry.gauge("sqs.in_flight", inFlight);
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        this.queueUrl = queueUrlResolver.resolveQueueUrl(
            properties.getQueueName(),
            properties.getQueueUrl(),
            "sqs.queue-name",
            "sqs.queue-url"
        );
        running = true;
        for (int i = 0; i < pollerThreads; i++) {
            pollerExecutor.submit(this::pollLoop);
        }
        log.info(
            "event=sqs_consumer_started queue_url={} max_in_flight={} poller_threads={} processing_threads={}",
            queueUrl,
            maxInFlight,
            pollerThreads,
            properties.getProcessingThreads()
        );
    }

    @Override
    public void stop() {
        running = false;
        pollerExecutor.shutdownNow();
        processingExecutor.shutdown();
        log.info("event=sqs_consumer_stopped queue_url={}", queueUrl);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void pollLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            if (inFlight.get() >= maxInFlight) {
                sleepBackoff();
                continue;
            }

            ReceiveMessageRequest request = buildReceiveRequest();
            ReceiveMessageResponse response;
            long pollStart = System.nanoTime();
            try {
                response = sqsClient.receiveMessage(request);
                pollCount.increment();
                pollLatency.record(System.nanoTime() - pollStart, TimeUnit.NANOSECONDS);
            } catch (Exception ex) {
                pollFailure.increment();
                log.warn(
                    "event=sqs_poll_failed queue_url={}",
                    queueUrl,
                    ex
                );
                sleepBackoff();
                continue;
            }

            List<Message> messages = response.messages();
            if (messages == null || messages.isEmpty()) {
                continue;
            }
            messagesReceived.increment(messages.size());

            for (Message message : messages) {
                try {
                    inFlight.incrementAndGet();
                    processingExecutor.submit(() -> processMessage(message));
                } catch (RejectedExecutionException ex) {
                    inFlight.decrementAndGet();
                    log.warn("event=sqs_processing_queue_full queue_url={}", queueUrl);
                    sleepBackoff();
                    break;
                }
            }
        }
    }

    private void processMessage(Message message) {
        long start = System.nanoTime();
        try {
            SqsTransactionProcessor.ProcessedTransaction processed = processor.process(message.body());
            RuleResult result = processed.result();
            log.info(
                "event=sqs_decision transaction_id={} decision={} reason={} risk_score={} message_id={} queue_url={}",
                processed.request().getTransactionId(),
                result.decision(),
                result.reason(),
                result.riskScore(),
                message.messageId(),
                queueUrl
            );
            if (writeOutbox(processed, message)) {
                deleteMessage(message);
            }
            processSuccess.increment();
        } catch (Exception ex) {
            processFailure.increment();
            log.warn(
                "event=sqs_process_failed message_id={} queue_url={}",
                message.messageId(),
                queueUrl,
                ex
            );
        } finally {
            processLatency.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            inFlight.decrementAndGet();
        }
    }

    private boolean writeOutbox(SqsTransactionProcessor.ProcessedTransaction processed, Message message) {
        OutboxWriter outboxWriter = outboxWriterProvider.getIfAvailable();
        if (outboxWriter == null) {
            return true;
        }
        try {
            OutboxWriteResult result = outboxWriter.write(processed, message.messageId());
            log.info(
                "event=outbox_written outbox_id={} status={} transaction_id={}",
                result.outboxId(),
                result.created() ? "created" : "duplicate",
                processed.request().getTransactionId()
            );
            return true;
        } catch (Exception ex) {
            log.warn(
                "event=outbox_write_failed message_id={} transaction_id={}",
                message.messageId(),
                processed.request().getTransactionId(),
                ex
            );
            return false;
        }
    }

    private void deleteMessage(Message message) {
        try {
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build());
        } catch (Exception ex) {
            log.warn(
                "event=sqs_delete_failed message_id={} queue_url={}",
                message.messageId(),
                queueUrl,
                ex
            );
        }
    }

    private ReceiveMessageRequest buildReceiveRequest() {
        int maxMessages = Math.min(10, Math.max(1, properties.getMaxMessages()));
        int waitTimeSeconds = Math.max(0, Math.min(20, properties.getWaitTimeSeconds()));
        ReceiveMessageRequest.Builder builder = ReceiveMessageRequest.builder()
            .queueUrl(queueUrl)
            .maxNumberOfMessages(maxMessages)
            .waitTimeSeconds(waitTimeSeconds);

        if (properties.getVisibilityTimeoutSeconds() > 0) {
            builder.visibilityTimeout(properties.getVisibilityTimeoutSeconds());
        }

        return builder.build();
    }

    private void sleepBackoff() {
        try {
            long backoffMillis = Math.max(0, properties.getPollerBackoffMillis());
            TimeUnit.MILLISECONDS.sleep(backoffMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static int resolveMaxInFlight(SqsProperties properties) {
        if (properties.getMaxInFlight() > 0) {
            return properties.getMaxInFlight();
        }
        return Math.max(1, properties.getProcessingThreads()) * Math.max(1, properties.getMaxMessages());
    }
}
