package org.fintech.sqs;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.fintech.rules.RuleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
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
    private final AtomicInteger inFlight;
    private final int maxInFlight;
    private volatile boolean running = false;

    public SqsTransactionConsumer(
        SqsClient sqsClient,
        SqsProperties properties,
        SqsTransactionProcessor processor,
        ExecutorService sqsProcessingExecutor
    ) {
        this.sqsClient = sqsClient;
        this.properties = properties;
        this.processor = processor;
        this.processingExecutor = sqsProcessingExecutor;
        this.pollerExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("sqs-poller");
            return thread;
        });
        this.inFlight = new AtomicInteger();
        this.maxInFlight = resolveMaxInFlight(properties);
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        pollerExecutor.submit(this::pollLoop);
        log.info("SQS consumer started for queue {}", properties.getQueueUrl());
    }

    @Override
    public void stop() {
        running = false;
        pollerExecutor.shutdownNow();
        processingExecutor.shutdown();
        log.info("SQS consumer stopped");
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
                log.warn("Failed to poll SQS queue {}", properties.getQueueUrl(), ex);
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
                    log.warn("SQS processing queue is full; backing off");
                    sleepBackoff();
                    break;
                }
            }
        }
    }

    private void processMessage(Message message) {
        try {
            RuleResult result = processor.process(message.body());
            log.info(
                "Processed SQS message {} with decision {}, reason {}, score {}",
                message.messageId(),
                result.decision(),
                result.reason(),
                result.riskScore()
            );
            deleteMessage(message);
        } catch (Exception ex) {
            log.warn("Failed to process SQS message {}", message.messageId(), ex);
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
            log.warn("Failed to delete SQS message {}", message.messageId(), ex);
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
