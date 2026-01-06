package org.fintech.rules;

import java.util.Optional;
import org.fintech.proto.v1.Decision;
import org.fintech.proto.v1.Reason;
import org.fintech.proto.v1.TransactionRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class AmountReviewRule implements TransactionRule {

    private final double reviewThreshold;

    public AmountReviewRule(@Value("${fraud.rules.amount-review-threshold:5000}") double reviewThreshold) {
        this.reviewThreshold = reviewThreshold;
    }

    @Override
    public Optional<RuleResult> apply(TransactionRequest request) {
        if (request.getAmount() >= reviewThreshold) {
            return Optional.of(new RuleResult(Decision.REVIEW, Reason.AMOUNT_REQUIRES_REVIEW, 0.7));
        }
        return Optional.empty();
    }
}
