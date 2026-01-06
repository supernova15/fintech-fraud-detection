package org.fintech.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.fintech.proto.v1.Decision;
import org.fintech.proto.v1.Reason;
import org.fintech.proto.v1.TransactionRequest;
import org.junit.jupiter.api.Test;

class AmountRulesTest {

    @Test
    void amountDenyRuleRejectsAtOrAboveThreshold() {
        AmountDenyRule rule = new AmountDenyRule(100.0);

        RuleResult result = rule.apply(baseRequest().setAmount(100.0).build()).orElseThrow();

        assertThat(result.decision()).isEqualTo(Decision.REJECT);
        assertThat(result.reason()).isEqualTo(Reason.AMOUNT_EXCEEDS_HARD_LIMIT);
        assertThat(result.riskScore()).isEqualTo(0.95);
    }

    @Test
    void amountDenyRuleSkipsBelowThreshold() {
        AmountDenyRule rule = new AmountDenyRule(100.0);

        assertThat(rule.apply(baseRequest().setAmount(99.99).build())).isEmpty();
    }

    @Test
    void amountReviewRuleFlagsAtOrAboveThreshold() {
        AmountReviewRule rule = new AmountReviewRule(50.0);

        RuleResult result = rule.apply(baseRequest().setAmount(50.0).build()).orElseThrow();

        assertThat(result.decision()).isEqualTo(Decision.REVIEW);
        assertThat(result.reason()).isEqualTo(Reason.AMOUNT_REQUIRES_REVIEW);
        assertThat(result.riskScore()).isEqualTo(0.7);
    }

    @Test
    void amountReviewRuleSkipsBelowThreshold() {
        AmountReviewRule rule = new AmountReviewRule(50.0);

        assertThat(rule.apply(baseRequest().setAmount(49.0).build())).isEmpty();
    }

    @Test
    void defaultApproveRuleAlwaysApproves() {
        DefaultApproveRule rule = new DefaultApproveRule(0.1);

        RuleResult result = rule.apply(baseRequest().setAmount(1.0).build()).orElseThrow();

        assertThat(result.decision()).isEqualTo(Decision.APPROVE);
        assertThat(result.reason()).isEqualTo(Reason.LOW_RISK_AMOUNT);
        assertThat(result.riskScore()).isEqualTo(0.1);
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
