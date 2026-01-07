package org.fintech.outbox;

import java.net.URI;
import org.fintech.sqs.SqsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;

@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfig {

    @Bean
    @ConditionalOnProperty(prefix = "outbox", name = "enabled", havingValue = "true")
    DynamoDbClient dynamoDbClient(OutboxProperties outboxProperties, SqsProperties sqsProperties) {
        String region = resolveRegion(outboxProperties, sqsProperties);
        if (!StringUtils.hasText(region)) {
            throw new IllegalStateException("outbox.region must be set when outbox.enabled=true");
        }

        DynamoDbClientBuilder builder = DynamoDbClient.builder()
            .region(Region.of(region))
            .credentialsProvider(resolveCredentials(outboxProperties, sqsProperties));

        String endpoint = resolveEndpoint(outboxProperties, sqsProperties);
        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "outbox", name = "enabled", havingValue = "true")
    DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
            .dynamoDbClient(dynamoDbClient)
            .build();
    }

    private static AwsCredentialsProvider resolveCredentials(
        OutboxProperties outboxProperties,
        SqsProperties sqsProperties
    ) {
        if (StringUtils.hasText(outboxProperties.getAccessKey())
            && StringUtils.hasText(outboxProperties.getSecretKey())) {
            return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(outboxProperties.getAccessKey(), outboxProperties.getSecretKey())
            );
        }
        if (StringUtils.hasText(sqsProperties.getAccessKey())
            && StringUtils.hasText(sqsProperties.getSecretKey())) {
            return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(sqsProperties.getAccessKey(), sqsProperties.getSecretKey())
            );
        }
        return DefaultCredentialsProvider.create();
    }

    private static String resolveRegion(OutboxProperties outboxProperties, SqsProperties sqsProperties) {
        if (StringUtils.hasText(outboxProperties.getRegion())) {
            return outboxProperties.getRegion();
        }
        return sqsProperties.getRegion();
    }

    private static String resolveEndpoint(OutboxProperties outboxProperties, SqsProperties sqsProperties) {
        if (StringUtils.hasText(outboxProperties.getEndpointOverride())) {
            return outboxProperties.getEndpointOverride();
        }
        return sqsProperties.getEndpointOverride();
    }
}
