package org.fintech.sqs;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;

@Component
@ConditionalOnBean(SqsClient.class)
public class SqsQueueUrlResolver {

    private final SqsClient sqsClient;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public SqsQueueUrlResolver(SqsClient sqsClient) {
        this.sqsClient = sqsClient;
    }

    public String resolveQueueUrl(
        String queueName,
        String queueUrl,
        String queueNameProperty,
        String queueUrlProperty
    ) {
        String normalizedUrl = normalizeQueueUrl(queueUrl);
        if (StringUtils.hasText(normalizedUrl)) {
            return normalizedUrl;
        }
        if (!StringUtils.hasText(queueName)) {
            throw new IllegalStateException(queueNameProperty + " or " + queueUrlProperty + " must be set");
        }
        return cache.computeIfAbsent(queueName, name -> sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
            .queueName(name)
            .build()).queueUrl());
    }

    private static String normalizeQueueUrl(String queueUrl) {
        if (!StringUtils.hasText(queueUrl)) {
            return null;
        }
        String trimmed = queueUrl.trim();
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            return null;
        }
        return trimmed;
    }
}
