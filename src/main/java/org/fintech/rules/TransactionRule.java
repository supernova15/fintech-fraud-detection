package org.fintech.rules;

import java.util.Optional;
import org.fintech.proto.v1.TransactionRequest;

public interface TransactionRule {
    Optional<RuleResult> apply(TransactionRequest request);
}
