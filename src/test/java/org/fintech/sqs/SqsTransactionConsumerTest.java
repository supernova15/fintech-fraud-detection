package org.fintech.sqs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.InvalidProtocolBufferException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.fintech.outbox.OutboxWriter;
import org.fintech.proto.v1.Decision;
import org.fintech.proto.v1.Reason;
import org.fintech.proto.v1.TransactionRequest;
import org.fintech.rules.RuleResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;

class SqsTransactionConsumerTest {

    @Test
    void processMessageDeletesWhenOutboxWriterMissing() throws Exception {
        SqsClient sqsClient = mock(SqsClient.class);
        SqsProperties properties = new SqsProperties();
        properties.setQueueUrl("queue-url");
        SqsTransactionProcessor processor = mock(SqsTransactionProcessor.class);
        ObjectProvider<OutboxWriter> outboxWriterProvider = mock(ObjectProvider.class);
        when(outboxWriterProvider.getIfAvailable()).thenReturn(null);

        TransactionRequest request = TransactionRequest.newBuilder()
            .setTransactionId("tx-1")
            .build();
        RuleResult result = new RuleResult(Decision.APPROVE, Reason.LOW_RISK_AMOUNT, 0.1);
        SqsTransactionProcessor.ProcessedTransaction processed =
            new SqsTransactionProcessor.ProcessedTransaction(request, result);
        when(processor.process("payload")).thenReturn(processed);

        ExecutorService processingExecutor = newDirectExecutorService();
        ExecutorService pollerExecutor = newDirectExecutorService();
        SqsTransactionConsumer consumer = new SqsTransactionConsumer(
            sqsClient,
            properties,
            processor,
            processingExecutor,
            pollerExecutor,
            outboxWriterProvider,
            new SimpleMeterRegistry()
        );

        Message message = Message.builder()
            .messageId("msg-1")
            .receiptHandle("receipt-1")
            .body("payload")
            .build();

        setInFlight(consumer, 1);
        invokeProcessMessage(consumer, message);

        ArgumentCaptor<DeleteMessageRequest> captor = ArgumentCaptor.forClass(DeleteMessageRequest.class);
        verify(sqsClient).deleteMessage(captor.capture());
        DeleteMessageRequest deleteRequest = captor.getValue();
        assertThat(deleteRequest.queueUrl()).isEqualTo("queue-url");
        assertThat(deleteRequest.receiptHandle()).isEqualTo("receipt-1");
        assertThat(getInFlight(consumer)).isZero();
    }

    @Test
    void processMessageSkipsDeleteWhenOutboxWriteFails() throws Exception {
        SqsClient sqsClient = mock(SqsClient.class);
        SqsProperties properties = new SqsProperties();
        properties.setQueueUrl("queue-url");
        SqsTransactionProcessor processor = mock(SqsTransactionProcessor.class);
        OutboxWriter outboxWriter = mock(OutboxWriter.class);
        ObjectProvider<OutboxWriter> outboxWriterProvider = mock(ObjectProvider.class);
        when(outboxWriterProvider.getIfAvailable()).thenReturn(outboxWriter);

        TransactionRequest request = TransactionRequest.newBuilder()
            .setTransactionId("tx-2")
            .build();
        RuleResult result = new RuleResult(Decision.REVIEW, Reason.AMOUNT_REQUIRES_REVIEW, 0.7);
        SqsTransactionProcessor.ProcessedTransaction processed =
            new SqsTransactionProcessor.ProcessedTransaction(request, result);
        when(processor.process("payload")).thenReturn(processed);
        when(outboxWriter.write(processed, "msg-2")).thenThrow(new RuntimeException("boom"));

        ExecutorService processingExecutor = newDirectExecutorService();
        ExecutorService pollerExecutor = newDirectExecutorService();
        SqsTransactionConsumer consumer = new SqsTransactionConsumer(
            sqsClient,
            properties,
            processor,
            processingExecutor,
            pollerExecutor,
            outboxWriterProvider,
            new SimpleMeterRegistry()
        );

        Message message = Message.builder()
            .messageId("msg-2")
            .receiptHandle("receipt-2")
            .body("payload")
            .build();

        setInFlight(consumer, 1);
        invokeProcessMessage(consumer, message);

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
        assertThat(getInFlight(consumer)).isZero();
    }

    @Test
    void processMessageDeletesWhenNonRetryableFailure() throws Exception {
        SqsClient sqsClient = mock(SqsClient.class);
        SqsProperties properties = new SqsProperties();
        properties.setQueueUrl("queue-url");
        SqsTransactionProcessor processor = mock(SqsTransactionProcessor.class);
        ObjectProvider<OutboxWriter> outboxWriterProvider = mock(ObjectProvider.class);
        when(outboxWriterProvider.getIfAvailable()).thenReturn(null);
        when(processor.process("payload")).thenThrow(new InvalidProtocolBufferException("bad payload"));

        ExecutorService processingExecutor = newDirectExecutorService();
        ExecutorService pollerExecutor = newDirectExecutorService();
        SqsTransactionConsumer consumer = new SqsTransactionConsumer(
            sqsClient,
            properties,
            processor,
            processingExecutor,
            pollerExecutor,
            outboxWriterProvider,
            new SimpleMeterRegistry()
        );

        Message message = Message.builder()
            .messageId("msg-3")
            .receiptHandle("receipt-3")
            .body("payload")
            .build();

        setInFlight(consumer, 1);
        invokeProcessMessage(consumer, message);

        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
        assertThat(getInFlight(consumer)).isZero();
    }

    private static void invokeProcessMessage(SqsTransactionConsumer consumer, Message message) {
        try {
            Method method = SqsTransactionConsumer.class.getDeclaredMethod("processMessage", Message.class);
            method.setAccessible(true);
            method.invoke(consumer, message);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to invoke processMessage", ex);
        }
    }

    private static void setInFlight(SqsTransactionConsumer consumer, int value) {
        AtomicInteger inFlight = getInFlightRef(consumer);
        inFlight.set(value);
    }

    private static int getInFlight(SqsTransactionConsumer consumer) {
        return getInFlightRef(consumer).get();
    }

    private static AtomicInteger getInFlightRef(SqsTransactionConsumer consumer) {
        try {
            Field field = SqsTransactionConsumer.class.getDeclaredField("inFlight");
            field.setAccessible(true);
            return (AtomicInteger) field.get(consumer);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to access inFlight", ex);
        }
    }

    private static ExecutorService newDirectExecutorService() {
        return new AbstractExecutorService() {
            private boolean shutdown;

            @Override
            public void shutdown() {
                shutdown = true;
            }

            @Override
            public List<Runnable> shutdownNow() {
                shutdown = true;
                return List.of();
            }

            @Override
            public boolean isShutdown() {
                return shutdown;
            }

            @Override
            public boolean isTerminated() {
                return shutdown;
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) {
                return true;
            }

            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };
    }
}
