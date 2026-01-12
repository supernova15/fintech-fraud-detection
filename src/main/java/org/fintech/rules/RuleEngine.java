package org.fintech.rules;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.fintech.proto.v1.TransactionRequest;
import org.springframework.stereotype.Component;

@Component
public class RuleEngine {

    private final List<TransactionRule> rules;
    private final DefaultApproveRule defaultRule;
    private final Timer evaluateLatency;

    public RuleEngine(List<TransactionRule> rules, DefaultApproveRule defaultRule, MeterRegistry meterRegistry) {
        List<TransactionRule> orderedRules = new ArrayList<>(rules);
        if (orderedRules.stream().noneMatch(rule -> rule instanceof DefaultApproveRule)) {
            orderedRules.add(defaultRule);
        }
        this.rules = List.copyOf(orderedRules);
        this.defaultRule = defaultRule;
        this.evaluateLatency = meterRegistry.timer("rules.evaluate.latency");
    }

    public RuleResult evaluate(TransactionRequest request) {
        long start = System.nanoTime();
        try {
            for (TransactionRule rule : rules) {
                Optional<RuleResult> result = rule.apply(request);
                if (result.isPresent()) {
                    return result.get();
                }
            }
            return defaultRule.apply(request)
                .orElseThrow(() -> new IllegalStateException("Default rule did not return a decision"));
        } finally {
            evaluateLatency.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }
}
