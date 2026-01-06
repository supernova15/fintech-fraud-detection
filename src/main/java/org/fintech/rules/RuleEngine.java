package org.fintech.rules;

import java.util.List;
import org.fintech.proto.v1.TransactionRequest;
import org.springframework.stereotype.Component;

@Component
public class RuleEngine {

    private final List<TransactionRule> rules;

    public RuleEngine(List<TransactionRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public RuleResult evaluate(TransactionRequest request) {
        for (TransactionRule rule : rules) {
            RuleResult result = rule.apply(request).orElse(null);
            if (result != null) {
                return result;
            }
        }
        throw new IllegalStateException("No rule produced a decision for transaction " + request.getTransactionId());
    }
}
