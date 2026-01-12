package org.fintech.metrics;

import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Clock;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClientBuilder;

@Configuration
@EnableConfigurationProperties(CloudWatchMetricsProperties.class)
@ConditionalOnProperty(prefix = "management.metrics.export.cloudwatch", name = "enabled", havingValue = "true")
public class CloudWatchMetricsConfig {

    @Bean
    CloudWatchConfig cloudWatchConfig(CloudWatchMetricsProperties properties) {
        if (!StringUtils.hasText(properties.getNamespace())) {
            throw new IllegalStateException(
                "management.metrics.export.cloudwatch.namespace must be set when CloudWatch metrics are enabled"
            );
        }
        return new CloudWatchConfig() {
            @Override
            public String get(String key) {
                return properties.get(key);
            }
        };
    }

    @Bean(destroyMethod = "close")
    CloudWatchAsyncClient cloudWatchAsyncClient(CloudWatchMetricsProperties properties) {
        CloudWatchAsyncClientBuilder builder = CloudWatchAsyncClient.builder()
            .credentialsProvider(DefaultCredentialsProvider.create());

        if (StringUtils.hasText(properties.getRegion())) {
            builder.region(Region.of(properties.getRegion()));
        }
        if (StringUtils.hasText(properties.getEndpoint())) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }

        return builder.build();
    }

    @Bean
    CloudWatchMeterRegistry cloudWatchMeterRegistry(
        CloudWatchConfig cloudWatchConfig,
        CloudWatchAsyncClient cloudWatchAsyncClient,
        Clock clock
    ) {
        return new CloudWatchMeterRegistry(cloudWatchConfig, clock, cloudWatchAsyncClient);
    }
}
