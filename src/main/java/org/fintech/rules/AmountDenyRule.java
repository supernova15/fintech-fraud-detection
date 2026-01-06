package org.fintech.rules;

import java.util.Optional;
import org.fintech.proto.v1.Decision;
import org.fintech.proto.v1.Reason;
import org.fintech.proto.v1.TransactionRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class AmountDenyRule implements TransactionRule {

    private final double denyThreshold;

    public AmountDenyRule(@Value("${fraud.rules.amount-deny-threshold:10000}") double denyThreshold) {
        this.denyThreshold = denyThreshold;
    }

    @Override
    public Optional<RuleResult> apply(TransactionRequest request) {
        if (request.getAmount() >= denyThreshold) {
            return Optional.of(new RuleResult(Decision.REJECT, Reason.AMOUNT_EXCEEDS_HARD_LIMIT, 0.95));
        }
        return Optional.empty();
    }
}
