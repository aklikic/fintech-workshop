package com.example.akka.backoffice.api.models;

public class TransactionModels {

    public record StartTransactionRequest(
            String idempotencyKey,
            String transactionId,
            String cardPan,
            String cardExpiryDate,
            String cardCvv,
            int amount,
            String currency) {}

    public record StartTransactionResponse(String result) {}

    public record CaptureTransactionResponse(String result) {}

    public record CancelTransactionResponse(String result) {}

    public record Transaction(
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

    public record TransactionSummary(
            String idempotencyKey,
            String transactionId,
            String accountId,
            String authResult,
            String authStatus,
            String captureResult,
            String captureStatus,
            String cancelResult,
            String cancelStatus) {}

    public record TransactionsByAccountResponse(java.util.List<TransactionSummary> transactions) {}
}