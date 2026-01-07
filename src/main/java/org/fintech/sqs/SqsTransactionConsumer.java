package org.fintech.sqs;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.fintech.rules.RuleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    private final SqsTransactionProcessor processor;
    private final ExecutorService processingExecutor;
    private final ExecutorService pollerExecutor;
    private final int pollerThreads;
    private final AtomicInteger inFlight;
    private final int maxInFlight;
    private volatile boolean running = false;

    public SqsTransactionConsumer(
        SqsClient sqsClient,
        SqsProperties properties,
        SqsTransactionProcessor processor,
        @Qualifier("sqsProcessingExecutor") ExecutorService sqsProcessingExecutor,
        @Qualifier("sqsPollerExecutor") ExecutorService sqsPollerExecutor
    ) {
        this.sqsClient = sqsClient;
        this.properties = properties;
        this.processor = processor;
        this.processingExecutor = sqsProcessingExecutor;
        this.pollerThreads = Math.max(1, properties.getPollerThreads());
        this.pollerExecutor = sqsPollerExecutor;
        this.inFlight = new AtomicInteger();
        this.maxInFlight = resolveMaxInFlight(properties);
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        for (int i = 0; i < pollerThreads; i++) {
            pollerExecutor.submit(this::pollLoop);
        }
        log.info(
            "event=sqs_consumer_started queue_url={} max_in_flight={} poller_threads={} processing_threads={}",
            properties.getQueueUrl(),
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
        log.info("event=sqs_consumer_stopped queue_url={}", properties.getQueueUrl());
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
            try {
                response = sqsClient.receiveMessage(request);
            } catch (Exception ex) {
                log.warn(
                    "event=sqs_poll_failed queue_url={}",
                    properties.getQueueUrl(),
                    ex
                );
                sleepBackoff();
                continue;
            }

            List<Message> messages = response.messages();
            if (messages == null || messages.isEmpty()) {
                continue;
            }

            for (Message message : messages) {
                try {
                    inFlight.incrementAndGet();
                    processingExecutor.submit(() -> processMessage(message));
                } catch (RejectedExecutionException ex) {
                    inFlight.decrementAndGet();
                    log.warn("event=sqs_processing_queue_full queue_url={}", properties.getQueueUrl());
                    sleepBackoff();
                    break;
                }
            }
        }
    }

    private void processMessage(Message message) {
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
                properties.getQueueUrl()
            );
            deleteMessage(message);
        } catch (Exception ex) {
            log.warn(
                "event=sqs_process_failed message_id={} queue_url={}",
                message.messageId(),
                properties.getQueueUrl(),
                ex
            );
        } finally {
            inFlight.decrementAndGet();
        }
    }

    private void deleteMessage(Message message) {
        try {
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(properties.getQueueUrl())
                .receiptHandle(message.receiptHandle())
                .build());
        } catch (Exception ex) {
            log.warn(
                "event=sqs_delete_failed message_id={} queue_url={}",
                message.messageId(),
                properties.getQueueUrl(),
                ex
            );
        }
    }

    private ReceiveMessageRequest buildReceiveRequest() {
        int maxMessages = Math.min(10, Math.max(1, properties.getMaxMessages()));
        int waitTimeSeconds = Math.max(0, Math.min(20, properties.getWaitTimeSeconds()));
        ReceiveMessageRequest.Builder builder = ReceiveMessageRequest.builder()
            .queueUrl(properties.getQueueUrl())
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
