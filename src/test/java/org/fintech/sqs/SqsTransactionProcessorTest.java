package org.fintech.sqs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Base64;
import org.fintech.proto.v1.Decision;
import org.fintech.proto.v1.Reason;
import org.fintech.proto.v1.TransactionRequest;
import org.fintech.rules.RuleEngine;
import org.fintech.rules.RuleResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SqsTransactionProcessorTest {

    @Test
    void processDecodesBase64ProtoAndEvaluatesRules() throws Exception {
        RuleEngine ruleEngine = mock(RuleEngine.class);
        RuleResult expected = new RuleResult(Decision.APPROVE, Reason.LOW_RISK_AMOUNT, 0.1);
        when(ruleEngine.evaluate(any(TransactionRequest.class))).thenReturn(expected);

        TransactionRequest request = TransactionRequest.newBuilder()
            .setTransactionId("tx-1")
            .setAccountId("acct-1")
            .setAmount(125.5)
            .setMerchant("ACME")
            .setCurrency("USD")
            .setTimestamp("2026-01-06T16:06:34+08:00")
            .build();
        String messageBody = Base64.getEncoder().encodeToString(request.toByteArray());

        SqsTransactionProcessor processor = new SqsTransactionProcessor(ruleEngine);
        SqsTransactionProcessor.ProcessedTransaction processed = processor.process(messageBody);

        assertThat(processed.result()).isEqualTo(expected);
        ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(ruleEngine).evaluate(captor.capture());
        assertThat(captor.getValue()).isEqualTo(request);
        assertThat(processed.request()).isEqualTo(request);
    }

    @Test
    void parseMessageRejectsNonBase64Payloads() {
        SqsTransactionProcessor processor = new SqsTransactionProcessor(mock(RuleEngine.class));

        assertThatThrownBy(() -> processor.parseMessage("not-base64"))
            .isInstanceOf(InvalidProtocolBufferException.class)
            .hasMessageContaining("base64");
    }
}
