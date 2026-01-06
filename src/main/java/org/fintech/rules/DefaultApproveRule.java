package org.fintech.rules;

import java.util.Optional;
import org.fintech.proto.v1.Decision;
import org.fintech.proto.v1.Reason;
import org.fintech.proto.v1.TransactionRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Integer.MAX_VALUE)
public class DefaultApproveRule implements TransactionRule {

    @Override
    public Optional<RuleResult> apply(TransactionRequest request) {
        return Optional.of(new RuleResult(Decision.APPROVE, Reason.LOW_RISK_AMOUNT, 0.1));
    }
}
