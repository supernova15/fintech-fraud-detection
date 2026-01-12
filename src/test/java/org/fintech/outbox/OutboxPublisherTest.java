package org.fintech.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

class OutboxPublisherTest {

    @Test
    void publishRecordSkipsWhenClaimFails() {
        OutboxRepository repository = mock(OutboxRepository.class);
        OutboxProperties properties = baseProperties();
        SqsClient sqsClient = mock(SqsClient.class);
        OutboxPublisher publisher = new OutboxPublisher(repository, properties, sqsClient, new SimpleMeterRegistry());

        OutboxRecord record = baseRecord();
        when(repository.claimForPublish(any(OutboxRecord.class), anyLong())).thenReturn(false);

        invokePublishRecord(publisher, record);

        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
        verify(repository, never()).update(any(OutboxRecord.class));
    }

    @Test
    void publishRecordUpdatesStatusOnSuccess() {
        OutboxRepository repository = mock(OutboxRepository.class);
        OutboxProperties properties = baseProperties();
        SqsClient sqsClient = mock(SqsClient.class);
        OutboxPublisher publisher = new OutboxPublisher(repository, properties, sqsClient, new SimpleMeterRegistry());

        OutboxRecord record = baseRecord();
        when(repository.claimForPublish(any(OutboxRecord.class), anyLong())).thenReturn(true);

        invokePublishRecord(publisher, record);

        verify(sqsClient).sendMessage(any(SendMessageRequest.class));
        verify(repository).update(record);
        assertThat(record.getStatus()).isEqualTo(OutboxStatus.PUBLISHED.name());
    }

    private static OutboxProperties baseProperties() {
        OutboxProperties properties = new OutboxProperties();
        properties.setDecisionQueueUrl("queue-url");
        properties.setPublishClaimLeaseMillis(1000);
        properties.setPublishWorkers(1);
        return properties;
    }

    private static OutboxRecord baseRecord() {
        OutboxRecord record = new OutboxRecord();
        record.setOutboxId("outbox-1");
        record.setTransactionId("tx-1");
        record.setPayloadBase64("payload");
        record.setStatus(OutboxStatus.PENDING.name());
        record.setUpdatedAt(1000L);
        record.setAttempts(0);
        return record;
    }

    private static void invokePublishRecord(OutboxPublisher publisher, OutboxRecord record) {
        try {
            Method method = OutboxPublisher.class.getDeclaredMethod("publishRecord", OutboxRecord.class);
            method.setAccessible(true);
            method.invoke(publisher, record);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to invoke publishRecord", ex);
        }
    }
}
