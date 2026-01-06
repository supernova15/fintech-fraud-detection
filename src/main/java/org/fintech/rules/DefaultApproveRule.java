package org.fintech.rules;

import java.util.Optional;
import org.fintech.proto.v1.Decision;
import org.fintech.proto.v1.Reason;
import org.fintech.proto.v1.TransactionRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Integer.MAX_VALUE)
public class DefaultApproveRule implements TransactionRule {

    private final double approveRiskScore;

    public DefaultApproveRule(@Value("${fraud.rules.approve-risk-score:0.1}") double approveRiskScore) {
        this.approveRiskScore = approveRiskScore;
    }

    @Override
    public Optional<RuleResult> apply(TransactionRequest request) {
        return Optional.of(new RuleResult(Decision.APPROVE, Reason.LOW_RISK_AMOUNT, approveRiskScore));
    }
}
