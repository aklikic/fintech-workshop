package com.example.akka.backoffice.api;


public interface ApiGatewayModel {

    record Account(String accountId, int availableBalance, int postedBalance) {}

    record CreateAccountRequest(String accountId, int initialBalance) {}

    record GetAllAccountsResponse(java.util.List<Account> accounts) {}

    record Card(String pan, String expiryDate, String cvv, String accountId) {}

    record CardSummary(String pan, String expiryDate, String accountId) {}

    record GetAllCardsResponse(java.util.List<CardSummary> cards) {}

    record GetCardsByAccountResponse(java.util.List<CardSummary> cards) {}

    record StartTransactionRequest(
            String idempotencyKey,
            String transactionId,
            String cardPan,
            String cardExpiryDate,
            String cardCvv,
            int amount,
            String currency) {}

    record StartTransactionResponse(String result) {}

    record CaptureTransactionResponse(String result) {}

    record CancelTransactionResponse(String result) {}

    record Transaction(
            String idempotencyKey,
            String transactionId,
            String cardPan,
            String cardExpiryDate,
            String cardCvv,
            int amount,
            String currency,
            String authCode,
            String authResult,
            String authStatus,
            String captureResult,
            String captureStatus,
            String cancelResult,
            String cancelStatus) {}

    record TransactionSummary(
            String idempotencyKey,
            String transactionId,
            String accountId,
            String authResult,
            String authStatus,
            String captureResult,
            String captureStatus,
            String cancelResult,
            String cancelStatus) {}

    record TransactionsByAccountResponse(java.util.List<TransactionSummary> transactions) {}
}
