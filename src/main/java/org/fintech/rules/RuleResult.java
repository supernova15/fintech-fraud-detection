package org.fintech.rules;

import org.fintech.proto.v1.Decision;
import org.fintech.proto.v1.Reason;

public record RuleResult(Decision decision, Reason reason, double riskScore) {}
