package org.fintech.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CloudWatchMetricsPropertiesTest {

    @Test
    void getReturnsNormalizedMicrometerKeys() {
        CloudWatchMetricsProperties properties = new CloudWatchMetricsProperties();
        properties.setEnabled(true);
        properties.setNamespace("Fintech/FraudDetection");
        properties.setStep(Duration.ofSeconds(30));
        properties.setBatchSize(500);
        properties.setNumThreads(3);
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(12));

        String stepValue = Duration.ofSeconds(30).toString();

        assertThat(properties.get("enabled")).isEqualTo("true");
        assertThat(properties.get("cloudwatch.enabled")).isEqualTo("true");
        assertThat(properties.get("namespace")).isEqualTo("Fintech/FraudDetection");
        assertThat(properties.get("cloudwatch.namespace")).isEqualTo("Fintech/FraudDetection");
        assertThat(properties.get("step")).isEqualTo(stepValue);
        assertThat(properties.get("cloudwatch.step")).isEqualTo(stepValue);
        assertThat(properties.get("batchSize")).isEqualTo("500");
        assertThat(properties.get("cloudwatch.batchSize")).isEqualTo("500");
        assertThat(properties.get("numThreads")).isEqualTo("3");
        assertThat(properties.get("connectTimeout")).isEqualTo(Duration.ofSeconds(2).toString());
        assertThat(properties.get("readTimeout")).isEqualTo(Duration.ofSeconds(12).toString());
        assertThat(properties.get("unknown")).isNull();
    }
}
