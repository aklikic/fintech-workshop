package com.example.akka.backoffice.api.models;

public class AccountModels {

    public record Account(String accountId, int availableBalance, int postedBalance) {}

    public record CreateAccountRequest(String accountId, int initialBalance) {}

    public record AuthorizeTransactionRequest(String transactionId, int amount) {}

    public record AuthorizeTransactionResponse(String authCode, String authResult, String authStatus) {}

    public record CaptureTransactionRequest(String transactionId) {}

    public record CaptureTransactionResponse(String captureResult, String captureStatus) {}

    public record CancelTransactionRequest(String transactionId) {}
    public record CancelTransactionResponse(String cancelResult, String cancelStatus) {}

    public record GetAllAccountsResponse(java.util.List<Account> accounts) {}
}