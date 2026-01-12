package org.fintech.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.stub.StreamObserver;
import org.fintech.proto.v1.Decision;
import org.fintech.proto.v1.RiskAssessment;
import org.fintech.proto.v1.TransactionRequest;
import org.fintech.proto.v1.Reason;
import org.fintech.rules.RuleEngine;
import org.fintech.rules.RuleResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FraudDetectionGrpcServiceTest {

    @Test
    void evaluateTransactionUsesRuleEngineAndReturnsAssessment() {
        RuleEngine ruleEngine = mock(RuleEngine.class);
        RuleResult expected = new RuleResult(Decision.REVIEW, Reason.AMOUNT_REQUIRES_REVIEW, 0.7);
        when(ruleEngine.evaluate(any(TransactionRequest.class))).thenReturn(expected);

        FraudDetectionGrpcService service = new FraudDetectionGrpcService(ruleEngine);
        TransactionRequest request = TransactionRequest.newBuilder()
            .setTransactionId("tx-900")
            .setAccountId("acct-1")
            .setAmount(7000.0)
            .setMerchant("ACME")
            .setCurrency("USD")
            .setTimestamp("2026-01-06T16:06:34+08:00")
            .build();

        CapturingObserver observer = new CapturingObserver();
        service.evaluateTransaction(request, observer);

        ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(ruleEngine).evaluate(captor.capture());
        assertThat(captor.getValue()).isEqualTo(request);

        assertThat(observer.error).isNull();
        assertThat(observer.completed).isTrue();
        assertThat(observer.value).isNotNull();
        assertThat(observer.value.getTransactionId()).isEqualTo("tx-900");
        assertThat(observer.value.getDecision()).isEqualTo(expected.decision());
        assertThat(observer.value.getReason()).isEqualTo(expected.reason());
        assertThat(observer.value.getRiskScore()).isEqualTo(expected.riskScore());
    }

    private static final class CapturingObserver implements StreamObserver<RiskAssessment> {
        private RiskAssessment value;
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(RiskAssessment value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }
}
