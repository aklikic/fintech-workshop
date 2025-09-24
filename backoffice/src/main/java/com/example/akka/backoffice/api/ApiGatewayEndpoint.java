package com.example.akka.backoffice.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.*;
import akka.javasdk.http.AbstractHttpEndpoint;
import com.example.akka.account.api.*;
import com.example.akka.payments.api.CardGrpcEndpointClient;
import com.example.akka.payments.api.TransactionGrpcEndpointClient;

@HttpEndpoint("/api")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class ApiGatewayEndpoint extends AbstractHttpEndpoint {

    private final AccountGrpcEndpointClient accountClient;
    private final CardGrpcEndpointClient cardClient;
    private final TransactionGrpcEndpointClient transactionClient;

    public ApiGatewayEndpoint(
            AccountGrpcEndpointClient accountClient,
            CardGrpcEndpointClient cardClient,
            TransactionGrpcEndpointClient transactionClient) {
        this.accountClient = accountClient;
        this.cardClient = cardClient;
        this.transactionClient = transactionClient;
    }

    @Post("/accounts")
    public ApiGatewayModel.Account createAccount(ApiGatewayModel.CreateAccountRequest request) {
        var grpcRequest = CreateAccountRequest.newBuilder()
                .setAccountId(request.accountId())
                .setInitialBalance(request.initialBalance())
                .build();

        var grpcResponse = accountClient.createAccount().invoke(grpcRequest);
        return new ApiGatewayModel.Account(
                grpcResponse.getAccountId(),
                grpcResponse.getAvailableBalance(),
                grpcResponse.getPostedBalance()
        );
    }

    @Get("/accounts/{accountId}")
    public ApiGatewayModel.Account getAccount(String accountId) {
        var grpcRequest = GetAccountRequest.newBuilder()
                .setAccountId(accountId)
                .build();

        var grpcResponse = accountClient.getAccount().invoke(grpcRequest);
        return new ApiGatewayModel.Account(
                grpcResponse.getAccountId(),
                grpcResponse.getAvailableBalance(),
                grpcResponse.getPostedBalance()
        );
    }

    @Get("/accounts")
    public ApiGatewayModel.GetAllAccountsResponse getAllAccounts() {
        var grpcRequest = com.example.akka.account.api.GetAllAccountsRequest.newBuilder().build();

        var grpcResponse = accountClient.getAllAccounts().invoke(grpcRequest);
        var accounts = grpcResponse.getAccountsList().stream()
                .map(account -> new ApiGatewayModel.Account(
                        account.getAccountId(),
                        account.getAvailableBalance(),
                        account.getPostedBalance()
                ))
                .toList();

        return new ApiGatewayModel.GetAllAccountsResponse(accounts);
    }

    @Post("/cards")
    public ApiGatewayModel.Card createCard(ApiGatewayModel.Card card) {
        var grpcRequest = com.example.akka.payments.api.Card.newBuilder()
                .setPan(card.pan())
                .setExpiryDate(card.expiryDate())
                .setCvv(card.cvv())
                .setAccountId(card.accountId())
                .build();

        var grpcResponse = cardClient.createCard().invoke(grpcRequest);
        return new ApiGatewayModel.Card(
                grpcResponse.getPan(),
                grpcResponse.getExpiryDate(),
                grpcResponse.getCvv(),
                grpcResponse.getAccountId()
        );
    }

    @Get("/cards/{pan}")
    public ApiGatewayModel.Card getCard(String pan) {
        var grpcRequest = com.example.akka.payments.api.GetCardRequest.newBuilder()
                .setPan(pan)
                .build();

        var grpcResponse = cardClient.getCard().invoke(grpcRequest);
        return new ApiGatewayModel.Card(
                grpcResponse.getPan(),
                grpcResponse.getExpiryDate(),
                grpcResponse.getCvv(),
                grpcResponse.getAccountId()
        );
    }

    @Post("/transactions/start")
    public ApiGatewayModel.StartTransactionResponse startTransaction(ApiGatewayModel.StartTransactionRequest request) {
        var grpcRequest = com.example.akka.payments.api.StartTransactionRequest.newBuilder()
                .setIdempotencyKey(request.idempotencyKey())
                .setTransactionId(request.transactionId())
                .setCardPan(request.cardPan())
                .setCardExpiryDate(request.cardExpiryDate())
                .setCardCvv(request.cardCvv())
                .setAmount(request.amount())
                .setCurrency(request.currency())
                .build();

        var grpcResponse = transactionClient.startTransaction().invoke(grpcRequest);
        return new ApiGatewayModel.StartTransactionResponse(grpcResponse.getResult().name());
    }

    @Get("/transactions/{idempotencyKey}")
    public ApiGatewayModel.Transaction getTransaction(String idempotencyKey) {
        var grpcRequest = com.example.akka.payments.api.GetTransactionRequest.newBuilder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        var grpcResponse = transactionClient.getTransaction().invoke(grpcRequest);
        return new ApiGatewayModel.Transaction(
                grpcResponse.getIdempotencyKey(),
                grpcResponse.getTransactionId(),
                grpcResponse.getCardPan(),
                grpcResponse.getCardExpiryDate(),
                grpcResponse.getCardCvv(),
                grpcResponse.getAmount(),
                grpcResponse.getCurrency(),
                grpcResponse.getAuthCode(),
                grpcResponse.getAuthResult().name(),
                grpcResponse.getAuthStatus().name(),
                grpcResponse.getCaptureResult().name(),
                grpcResponse.getCaptureStatus().name(),
                grpcResponse.getCancelResult().name(),
                grpcResponse.getCancelStatus().name()
        );
    }

    @Post("/transactions/{idempotencyKey}/capture")
    public ApiGatewayModel.CaptureTransactionResponse captureTransactionByKey(String idempotencyKey) {
        var grpcRequest = com.example.akka.payments.api.StartCaptureTransactionRequest.newBuilder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        var grpcResponse = transactionClient.captureTransaction().invoke(grpcRequest);
        return new ApiGatewayModel.CaptureTransactionResponse(grpcResponse.getResult().name());
    }

    @Post("/transactions/{idempotencyKey}/cancel")
    public ApiGatewayModel.CancelTransactionResponse cancelTransactionByKey(String idempotencyKey) {
        var grpcRequest = com.example.akka.payments.api.StartCancelTransactionRequest.newBuilder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        var grpcResponse = transactionClient.cancelTransaction().invoke(grpcRequest);
        return new ApiGatewayModel.CancelTransactionResponse(grpcResponse.getResult().name());
    }

    @Get("/accounts/{accountId}/transactions")
    public ApiGatewayModel.TransactionsByAccountResponse getTransactionsByAccount(String accountId) {
        var grpcRequest = com.example.akka.payments.api.GetTransactionsByAccountRequest.newBuilder()
                .setAccountId(accountId)
                .build();

        var grpcResponse = transactionClient.getTransactionsByAccount().invoke(grpcRequest);

        var transactions = grpcResponse.getTransactionsList().stream()
                .map(t -> new ApiGatewayModel.TransactionSummary(
                        t.getIdempotencyKey(),
                        t.getTransactionId(),
                        t.getAccountId(),
                        t.getAuthResult(),
                        t.getAuthStatus(),
                        t.getCaptureResult(),
                        t.getCaptureStatus(),
                        t.getCancelResult(),
                        t.getCancelStatus()
                ))
                .toList();

        return new ApiGatewayModel.TransactionsByAccountResponse(transactions);
    }
}