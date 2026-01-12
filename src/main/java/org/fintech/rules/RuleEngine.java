package org.fintech.rules;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.fintech.proto.v1.TransactionRequest;
import org.springframework.stereotype.Component;

@Component
public class RuleEngine {

    private final List<TransactionRule> rules;
    private final Timer evaluateLatency;

    public RuleEngine(List<TransactionRule> rules, MeterRegistry meterRegistry) {
        this.rules = List.copyOf(rules);
        this.evaluateLatency = meterRegistry.timer("rules.evaluate.latency");
    }

    public RuleResult evaluate(TransactionRequest request) {
        long start = System.nanoTime();
        try {
            for (TransactionRule rule : rules) {
                RuleResult result = rule.apply(request).orElse(null);
                if (result != null) {
                    return result;
                }
            }
            throw new IllegalStateException("No rule produced a decision for transaction " + request.getTransactionId());
        } finally {
            evaluateLatency.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }
}
