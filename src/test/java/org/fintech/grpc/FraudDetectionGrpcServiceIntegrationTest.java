package org.fintech.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;
import org.fintech.proto.v1.Decision;
import org.fintech.proto.v1.FraudDetectionServiceGrpc;
import org.fintech.proto.v1.RiskAssessment;
import org.fintech.proto.v1.TransactionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FraudDetectionGrpcServiceIntegrationTest {

    private static int grpcPort;

    @DynamicPropertySource
    static void registerGrpcProperties(DynamicPropertyRegistry registry) {
        grpcPort = findAvailablePort();
        registry.add("grpc.server.port", () -> grpcPort);
        registry.add("grpc.server.address", () -> "127.0.0.1");
    }

    @Test
    void evaluateTransactionReturnsDecisionAndScore() throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", grpcPort)
            .usePlaintext()
            .build();
        try {
            FraudDetectionServiceGrpc.FraudDetectionServiceBlockingStub stub =
                FraudDetectionServiceGrpc.newBlockingStub(channel);

            RiskAssessment lowRisk = stub.evaluateTransaction(baseRequest().setAmount(250.0).build());
            assertThat(lowRisk.getDecision()).isEqualTo(Decision.APPROVE);
            assertThat(lowRisk.getRiskScore()).isEqualTo(0.1);

            RiskAssessment review = stub.evaluateTransaction(baseRequest().setAmount(7500.0).build());
            assertThat(review.getDecision()).isEqualTo(Decision.REVIEW);
            assertThat(review.getRiskScore()).isEqualTo(0.7);

            RiskAssessment reject = stub.evaluateTransaction(baseRequest().setAmount(15000.0).build());
            assertThat(reject.getDecision()).isEqualTo(Decision.REJECT);
            assertThat(reject.getRiskScore()).isEqualTo(0.95);
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static TransactionRequest.Builder baseRequest() {
        return TransactionRequest.newBuilder()
            .setTransactionId("tx-123")
            .setAccountId("acct-9")
            .setMerchant("ACME")
            .setCurrency("USD")
            .setTimestamp("2026-01-06T16:06:34+08:00");
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
