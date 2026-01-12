package org.fintech.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

class CloudWatchMetricsConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(CloudWatchMetricsConfig.class)
        .withBean(Clock.class, () -> Clock.SYSTEM);

    @Test
    void failsWhenNamespaceMissing() {
        contextRunner
            .withPropertyValues("management.metrics.export.cloudwatch.enabled=true")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasMessageContaining("management.metrics.export.cloudwatch.namespace must be set");
            });
    }

    @Test
    void registersCloudWatchBeansWhenEnabled() {
        contextRunner
            .withPropertyValues(
                "management.metrics.export.cloudwatch.enabled=true",
                "management.metrics.export.cloudwatch.namespace=TestNamespace",
                "management.metrics.export.cloudwatch.region=us-east-1"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(CloudWatchMeterRegistry.class);
                assertThat(context).hasSingleBean(CloudWatchAsyncClient.class);
            });
    }
}
