package org.fintech.tools;

import java.util.Base64;
import org.fintech.proto.v1.TransactionRequest;

public final class SqsTransactionRequestEncoder {

    private SqsTransactionRequestEncoder() {
    }

    public static void main(String[] args) {
        String transactionId = env("TX_ID", "tx-123");
        String accountId = env("ACCOUNT_ID", "acct-9");
        double amount = envDouble("AMOUNT", 125.50);
        String merchant = env("MERCHANT", "ACME");
        String currency = env("CURRENCY", "USD");
        String timestamp = env("TIMESTAMP", "2026-01-06T16:06:34+08:00");

        TransactionRequest request = TransactionRequest.newBuilder()
            .setTransactionId(transactionId)
            .setAccountId(accountId)
            .setAmount(amount)
            .setMerchant(merchant)
            .setCurrency(currency)
            .setTimestamp(timestamp)
            .build();

        String encoded = Base64.getEncoder().encodeToString(request.toByteArray());
        System.out.println(encoded);
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private static double envDouble(String key, double defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
