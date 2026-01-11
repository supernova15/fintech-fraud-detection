package org.fintech.sqs;

import java.net.URI;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

@Configuration
@EnableConfigurationProperties(SqsProperties.class)
public class SqsConfig {

    @Bean
    @ConditionalOnProperty(prefix = "sqs", name = "enabled", havingValue = "true")
    SqsClient sqsClient(SqsProperties properties) {
        if (!StringUtils.hasText(properties.getQueueUrl())
            && !StringUtils.hasText(properties.getQueueName())) {
            throw new IllegalStateException("sqs.queue-name or sqs.queue-url must be set when sqs.enabled=true");
        }
        if (!StringUtils.hasText(properties.getRegion())) {
            throw new IllegalStateException("sqs.region must be set when sqs.enabled=true");
        }

        SqsClientBuilder builder = SqsClient.builder()
            .region(Region.of(properties.getRegion()))
            .credentialsProvider(resolveCredentials(properties));

        if (StringUtils.hasText(properties.getEndpointOverride())) {
            builder.endpointOverride(URI.create(properties.getEndpointOverride()));
        }

        return builder.build();
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "sqs", name = "enabled", havingValue = "true")
    ExecutorService sqsProcessingExecutor(SqsProperties properties) {
        int threads = Math.max(1, properties.getProcessingThreads());
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            threads,
            threads,
            properties.getProcessingKeepAliveSeconds(),
            TimeUnit.SECONDS,
            createQueue(properties.getProcessingQueueType(), properties.getProcessingQueueCapacity()),
            namedThreadFactory("sqs-worker-"),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(properties.isProcessingAllowCoreTimeout());
        return executor;
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "sqs", name = "enabled", havingValue = "true")
    ExecutorService sqsPollerExecutor(SqsProperties properties) {
        int threads = Math.max(1, properties.getPollerThreads());
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            threads,
            threads,
            properties.getPollerKeepAliveSeconds(),
            TimeUnit.SECONDS,
            createQueue(properties.getPollerQueueType(), properties.getPollerQueueCapacity()),
            namedThreadFactory("sqs-poller-")
        );
        executor.allowCoreThreadTimeOut(properties.isPollerAllowCoreTimeout());
        return executor;
    }

    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger index = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + index.getAndIncrement());
            return thread;
        };
    }

    private static BlockingQueue<Runnable> createQueue(String queueType, int queueCapacity) {
        String normalized = queueType == null ? "" : queueType.trim().toLowerCase();
        switch (normalized) {
            case "linked":
                if (queueCapacity > 0) {
                    return new LinkedBlockingQueue<>(queueCapacity);
                }
                return new LinkedBlockingQueue<>();
            case "array":
                int resolvedCapacity = queueCapacity > 0 ? queueCapacity : 1024;
                return new ArrayBlockingQueue<>(resolvedCapacity);
            case "synchronous":
            default:
                return new SynchronousQueue<>();
        }
    }

    private static software.amazon.awssdk.auth.credentials.AwsCredentialsProvider resolveCredentials(
        SqsProperties properties
    ) {
        if (StringUtils.hasText(properties.getAccessKey()) && StringUtils.hasText(properties.getSecretKey())) {
            return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())
            );
        }
        return DefaultCredentialsProvider.create();
    }
}
