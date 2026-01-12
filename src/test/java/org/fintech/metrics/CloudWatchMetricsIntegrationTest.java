package org.fintech.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.MockReset;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataResponse;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "management.metrics.export.cloudwatch.enabled=true",
        "management.metrics.export.cloudwatch.namespace=TestNamespace",
        "management.metrics.export.cloudwatch.region=us-east-1",
        "management.metrics.export.cloudwatch.endpoint=http://localhost:4566",
        "management.metrics.export.cloudwatch.step=30s"
    }
)
class CloudWatchMetricsIntegrationTest {

    private static int grpcPort;

    @Autowired
    private CloudWatchConfig cloudWatchConfig;

    @Autowired
    private CloudWatchMeterRegistry cloudWatchMeterRegistry;

    @MockBean(reset = MockReset.NONE)
    private CloudWatchAsyncClient cloudWatchAsyncClient;

    @DynamicPropertySource
    static void registerGrpcProperties(DynamicPropertyRegistry registry) {
        grpcPort = findAvailablePort();
        registry.add("grpc.server.port", () -> grpcPort);
        registry.add("grpc.server.address", () -> "127.0.0.1");
    }

    @BeforeEach
    void stubCloudWatchClient() {
        when(cloudWatchAsyncClient.putMetricData(any(PutMetricDataRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(PutMetricDataResponse.builder().build()));
    }

    @Test
    void registersCloudWatchRegistry() {
        assertThat(cloudWatchConfig.namespace()).isEqualTo("TestNamespace");
        assertThat(cloudWatchConfig.step()).isEqualTo(Duration.ofSeconds(30));
        assertThat(cloudWatchMeterRegistry).isNotNull();
        assertThat(cloudWatchAsyncClient).isNotNull();
    }

    private static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to allocate a gRPC test port", ex);
        }
    }
}
