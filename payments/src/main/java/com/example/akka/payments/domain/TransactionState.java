package com.example.akka.payments.domain;

public record TransactionState(
    String idempotencyKey,
    String transactionId,
    CardData cardData,
    String accountId,
    String authCode,
    AuthResult authResult,
    AuthStatus authStatus,
    boolean captured
) {
    
    public static TransactionState empty() {
        return new TransactionState("", "", CardData.empty(), "", "", AuthResult.declined, AuthStatus.ok, false);
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
                TransactionState.AuthResult.declined,
                TransactionState.AuthStatus.ok,
                false
        );
    }

    public TransactionState withCardValid(String accountId) {
        return new TransactionState(idempotencyKey, transactionId, cardData, accountId, authCode, authResult, authStatus, captured);
    }

    public TransactionState withAuthResult(String authCode, AuthResult authResult, AuthStatus authStatus) {
        return new TransactionState(idempotencyKey, transactionId, cardData, accountId, authCode, authResult, authStatus, captured);
    }
    
    public TransactionState withCaptured(boolean captured) {
        return new TransactionState(idempotencyKey, transactionId, cardData, accountId, authCode, authResult, authStatus, captured);
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
}