package org.fintech.grpc;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.fintech.proto.v1.Decision;
import org.fintech.proto.v1.FraudDetectionServiceGrpc;
import org.fintech.proto.v1.Reason;
import org.fintech.proto.v1.RiskAssessment;
import org.fintech.proto.v1.TransactionRequest;

@GrpcService
public class FraudDetectionGrpcService extends FraudDetectionServiceGrpc.FraudDetectionServiceImplBase {
    @Override
    public void evaluateTransaction(TransactionRequest request, StreamObserver<RiskAssessment> responseObserver) {
        double amount = request.getAmount();
        double riskScore;
        Decision decision;
        Reason reason;

        if (amount >= 10000.0) {
            riskScore = 0.95;
            decision = Decision.REJECT;
            reason = Reason.AMOUNT_EXCEEDS_HARD_LIMIT;
        } else if (amount >= 5000.0) {
            riskScore = 0.7;
            decision = Decision.REVIEW;
            reason = Reason.AMOUNT_REQUIRES_REVIEW;
        } else {
            riskScore = 0.1;
            decision = Decision.APPROVE;
            reason = Reason.LOW_RISK_AMOUNT;
        }

        RiskAssessment response = RiskAssessment.newBuilder()
            .setTransactionId(request.getTransactionId())
            .setRiskScore(riskScore)
            .setDecision(decision)
            .setReason(reason)
            .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
