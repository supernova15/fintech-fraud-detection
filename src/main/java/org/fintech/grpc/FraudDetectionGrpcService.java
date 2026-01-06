package org.fintech.grpc;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.fintech.proto.v1.FraudDetectionServiceGrpc;
import org.fintech.proto.v1.RiskAssessment;
import org.fintech.proto.v1.TransactionRequest;
import org.fintech.rules.RuleEngine;
import org.fintech.rules.RuleResult;

@GrpcService
public class FraudDetectionGrpcService extends FraudDetectionServiceGrpc.FraudDetectionServiceImplBase {

    private final RuleEngine ruleEngine;

    public FraudDetectionGrpcService(RuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    @Override
    public void evaluateTransaction(TransactionRequest request, StreamObserver<RiskAssessment> responseObserver) {
        RuleResult result = ruleEngine.evaluate(request);

        RiskAssessment response = RiskAssessment.newBuilder()
            .setTransactionId(request.getTransactionId())
            .setRiskScore(result.riskScore())
            .setDecision(result.decision())
            .setReason(result.reason())
            .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
