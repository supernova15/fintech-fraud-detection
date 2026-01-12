package org.fintech.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.fintech.proto.v1.Decision;
import org.fintech.proto.v1.TransactionRequest;
import org.junit.jupiter.api.Test;

class RuleEngineTest {

    @Test
    void evaluateReturnsFirstMatchingRuleInOrder() {
        DefaultApproveRule defaultRule = new DefaultApproveRule(0.1);
        RuleEngine engine = new RuleEngine(List.of(
            new AmountDenyRule(100.0),
            new AmountReviewRule(50.0)
        ), defaultRule, new SimpleMeterRegistry());

        RuleResult result = engine.evaluate(baseRequest().setAmount(150.0).build());

        assertThat(result.decision()).isEqualTo(Decision.REJECT);
    }

    @Test
    void evaluateFallsThroughToReviewAndApprove() {
        DefaultApproveRule defaultRule = new DefaultApproveRule(0.1);
        RuleEngine engine = new RuleEngine(List.of(
            new AmountDenyRule(100.0),
            new AmountReviewRule(50.0)
        ), defaultRule, new SimpleMeterRegistry());

        RuleResult reviewResult = engine.evaluate(baseRequest().setAmount(75.0).build());
        RuleResult approveResult = engine.evaluate(baseRequest().setAmount(10.0).build());

        assertThat(reviewResult.decision()).isEqualTo(Decision.REVIEW);
        assertThat(approveResult.decision()).isEqualTo(Decision.APPROVE);
    }

    @Test
    void evaluateFallsBackToDefaultRuleWhenMissing() {
        DefaultApproveRule defaultRule = new DefaultApproveRule(0.2);
        RuleEngine engine = new RuleEngine(List.of(
            new AmountDenyRule(100.0),
            new AmountReviewRule(50.0)
        ), defaultRule, new SimpleMeterRegistry());

        RuleResult result = engine.evaluate(baseRequest().setAmount(10.0).build());

        assertThat(result.decision()).isEqualTo(Decision.APPROVE);
        assertThat(result.riskScore()).isEqualTo(0.2);
    }

    private static TransactionRequest.Builder baseRequest() {
        return TransactionRequest.newBuilder()
            .setTransactionId("tx-123")
            .setAccountId("acct-9")
            .setMerchant("ACME")
            .setCurrency("USD")
            .setTimestamp("2026-01-06T16:06:34+08:00");
    }
}
