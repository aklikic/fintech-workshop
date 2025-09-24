package com.example.akka.payments.domain;

public record TransactionState(
    String idempotencyKey,
    String transactionId,
    CardData cardData,
    String accountId,
    String authCode,
    AuthResult authResult,
    AuthStatus authStatus,
    CaptureResult captureResult,
    CaptureStatus captureStatus,
    CancelResult cancelResult,
    CancelStatus cancelStatus
) {
    
    public static TransactionState empty() {
        return new TransactionState("", "", CardData.empty(), "", "", AuthResult.declined, AuthStatus.ok, CaptureResult.declined, CaptureStatus.ok, CancelResult.declined, CancelStatus.ok);
    }
    
    public boolean isEmpty() {
        return idempotencyKey.isEmpty();
    }

    public TransactionState init(String idempotencyKey, String transactionId, CardData cardData) {
        return new TransactionState(
                idempotencyKey,
                transactionId,
                cardData,
                "",
                "",
                authResult,
                authStatus,
                captureResult,
                captureStatus,
                cancelResult,
                cancelStatus
        );
    }

    public TransactionState withCardValid(String accountId) {
        return new TransactionState(idempotencyKey, transactionId, cardData, accountId, authCode, authResult, authStatus, captureResult, captureStatus, cancelResult, cancelStatus);
    }

    public TransactionState withAuthResult(String authCode, AuthResult authResult, AuthStatus authStatus) {
        return new TransactionState(idempotencyKey, transactionId, cardData, accountId, authCode, authResult, authStatus, captureResult, captureStatus, cancelResult, cancelStatus);
    }
    
    public TransactionState withCaptured(CaptureResult captureResult, CaptureStatus captureStatus) {
        return new TransactionState(idempotencyKey, transactionId, cardData, accountId, authCode, authResult, authStatus, captureResult, captureStatus, cancelResult, cancelStatus);
    }

    public TransactionState withCanceled(CancelResult cancelResult, CancelStatus cancelStatus) {
        return new TransactionState(idempotencyKey, transactionId, cardData, accountId, authCode, authResult, authStatus, captureResult, captureStatus, cancelResult, cancelStatus);
    }
    
    public record CardData(
        String cardPan,
        String cardExpiryDate,
        String cardCvv,
        int amount,
        String currency
    ) {
        public static CardData empty() {
            return new CardData("", "", "", 0, "");
        }
    }
    
    public enum AuthResult {
        authorised, declined
    }
    
    public enum AuthStatus {
        ok, card_not_found, insufficient_funds, account_closed, undiscosed, account_not_found
    }

    public enum CaptureResult {
        captured, declined
    }

    public enum CaptureStatus {
        ok, undiscosed, account_not_found, transaction_not_found
    }

    public enum CancelResult {
        canceled, declined
    }

    public enum CancelStatus {
        ok, undiscosed, account_not_found, transaction_not_found
    }

}