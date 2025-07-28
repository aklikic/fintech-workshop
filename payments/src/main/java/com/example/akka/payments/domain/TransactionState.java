package com.example.akka.payments.domain;

public record TransactionState(
    String idempotencyKey,
    String transactionId,
    CardData cardData,
    String authCode,
    AuthResult authResult,
    AuthStatus authStatus
) {
    
    public static TransactionState empty() {
        return new TransactionState("", "", CardData.empty(), "", AuthResult.declined, AuthStatus.ok);
    }
    
    public boolean isEmpty() {
        return idempotencyKey.isEmpty();
    }
    
    public TransactionState withAuthResult(String authCode, AuthResult authResult, AuthStatus authStatus) {
        return new TransactionState(idempotencyKey, transactionId, cardData, authCode, authResult, authStatus);
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