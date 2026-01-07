package org.fintech.sqs;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Base64;
import org.fintech.proto.v1.TransactionRequest;
import org.fintech.rules.RuleEngine;
import org.fintech.rules.RuleResult;
import org.springframework.stereotype.Component;

@Component
public class SqsTransactionProcessor {

    private final RuleEngine ruleEngine;

    public SqsTransactionProcessor(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    public ProcessedTransaction process(String messageBody) throws InvalidProtocolBufferException {
        TransactionRequest request = parseMessage(messageBody);
        RuleResult result = ruleEngine.evaluate(request);
        return new ProcessedTransaction(request, result);
    }

    TransactionRequest parseMessage(String messageBody) throws InvalidProtocolBufferException {
        if (messageBody == null || messageBody.isBlank()) {
            throw new InvalidProtocolBufferException("SQS message body is empty");
        }
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(messageBody);
        } catch (IllegalArgumentException ex) {
            InvalidProtocolBufferException wrapped =
                new InvalidProtocolBufferException("SQS message body is not base64-encoded protobuf");
            wrapped.initCause(ex);
            throw wrapped;
        }
        return TransactionRequest.parseFrom(payload);
    }

    record ProcessedTransaction(TransactionRequest request, RuleResult result) {}
}
