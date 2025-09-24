package com.example.akka.ui.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.*;
import akka.javasdk.http.AbstractHttpEndpoint;
import com.example.akka.account.api.*;
import com.example.akka.payments.api.CardGrpcEndpointClient;
import com.example.akka.payments.api.TransactionGrpcEndpointClient;
import com.example.akka.ui.api.models.AccountModels;
import com.example.akka.ui.api.models.CardModels;
import com.example.akka.ui.api.models.TransactionModels;

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
    public AccountModels.Account createAccount(AccountModels.CreateAccountRequest request) {
        var grpcRequest = CreateAccountRequest.newBuilder()
                .setAccountId(request.accountId())
                .setInitialBalance(request.initialBalance())
                .build();

        var grpcResponse = accountClient.createAccount().invoke(grpcRequest);
        return new AccountModels.Account(
                grpcResponse.getAccountId(),
                grpcResponse.getAvailableBalance(),
                grpcResponse.getPostedBalance()
        );
    }

    @Get("/accounts/{accountId}")
    public AccountModels.Account getAccount(String accountId) {
        var grpcRequest = GetAccountRequest.newBuilder()
                .setAccountId(accountId)
                .build();

        var grpcResponse = accountClient.getAccount().invoke(grpcRequest);
        return new AccountModels.Account(
                grpcResponse.getAccountId(),
                grpcResponse.getAvailableBalance(),
                grpcResponse.getPostedBalance()
        );
    }

    @Post("/accounts/{accountId}/authorize")
    public AccountModels.AuthorizeTransactionResponse authorizeTransaction(
            String accountId,
            AccountModels.AuthorizeTransactionRequest request) {

        var grpcRequest = AuthorizeTransactionRequest.newBuilder()
                .setAccountId(accountId)
                .setTransactionId(request.transactionId())
                .setAmount(request.amount())
                .build();

        var grpcResponse = accountClient.authorizeTransaction().invoke(grpcRequest);
        return new AccountModels.AuthorizeTransactionResponse(
                grpcResponse.getAuthCode(),
                grpcResponse.getAuthResult().name(),
                grpcResponse.getAuthStatus().name()
        );
    }

    @Post("/accounts/{accountId}/capture")
    public AccountModels.CaptureTransactionResponse captureTransaction(
            String accountId,
            AccountModels.CaptureTransactionRequest request) {

        var grpcRequest = CaptureTransactionRequest.newBuilder()
                .setAccountId(accountId)
                .setTransactionId(request.transactionId())
                .build();

        var grpcResponse = accountClient.captureTransaction().invoke(grpcRequest);
        return new AccountModels.CaptureTransactionResponse(grpcResponse.getSuccess());
    }

    @Get("/accounts/{accountId}/expenditure")
    public AccountModels.ExpenditureResponse getExpenditure(String accountId) {
        var grpcRequest = ExpenditureRequest.newBuilder()
                .setAccountId(accountId)
                .build();

        var grpcResponse = accountClient.getExpenditure().invoke(grpcRequest);
        return new AccountModels.ExpenditureResponse(
                grpcResponse.getAccountId(),
                grpcResponse.getMoneyIn(),
                grpcResponse.getMoneyOut()
        );
    }

    @Get("/accounts")
    public AccountModels.GetAllAccountsResponse getAllAccounts() {
        var grpcRequest = com.example.akka.account.api.GetAllAccountsRequest.newBuilder().build();

        var grpcResponse = accountClient.getAllAccounts().invoke(grpcRequest);
        var accounts = grpcResponse.getAccountsList().stream()
                .map(account -> new AccountModels.Account(
                        account.getAccountId(),
                        account.getAvailableBalance(),
                        account.getPostedBalance()
                ))
                .toList();

        return new AccountModels.GetAllAccountsResponse(accounts);
    }

    @Post("/cards")
    public CardModels.Card createCard(CardModels.Card card) {
        var grpcRequest = com.example.akka.payments.api.Card.newBuilder()
                .setPan(card.pan())
                .setExpiryDate(card.expiryDate())
                .setCvv(card.cvv())
                .setAccountId(card.accountId())
                .build();

        var grpcResponse = cardClient.createCard().invoke(grpcRequest);
        return new CardModels.Card(
                grpcResponse.getPan(),
                grpcResponse.getExpiryDate(),
                grpcResponse.getCvv(),
                grpcResponse.getAccountId()
        );
    }

    @Get("/cards/{pan}")
    public CardModels.Card getCard(String pan) {
        var grpcRequest = com.example.akka.payments.api.GetCardRequest.newBuilder()
                .setPan(pan)
                .build();

        var grpcResponse = cardClient.getCard().invoke(grpcRequest);
        return new CardModels.Card(
                grpcResponse.getPan(),
                grpcResponse.getExpiryDate(),
                grpcResponse.getCvv(),
                grpcResponse.getAccountId()
        );
    }

    @Post("/cards/validate")
    public CardModels.ValidateCardResponse validateCard(CardModels.ValidateCardRequest request) {
        var grpcRequest = com.example.akka.payments.api.ValidateCardRequest.newBuilder()
                .setPan(request.pan())
                .setExpiryDate(request.expiryDate())
                .setCvv(request.cvv())
                .build();

        var grpcResponse = cardClient.validateCard().invoke(grpcRequest);
        return new CardModels.ValidateCardResponse(
                grpcResponse.getIsValid(),
                grpcResponse.getMessage()
        );
    }

    @Post("/transactions/start")
    public TransactionModels.StartTransactionResponse startTransaction(TransactionModels.StartTransactionRequest request) {
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
        return new TransactionModels.StartTransactionResponse(grpcResponse.getResult().name());
    }

    @Get("/transactions/{idempotencyKey}")
    public TransactionModels.Transaction getTransaction(String idempotencyKey) {
        var grpcRequest = com.example.akka.payments.api.GetTransactionRequest.newBuilder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        var grpcResponse = transactionClient.getTransaction().invoke(grpcRequest);
        return new TransactionModels.Transaction(
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
                grpcResponse.getCaptured()
        );
    }

    @Post("/transactions/{idempotencyKey}/capture")
    public TransactionModels.CaptureTransactionResponse captureTransactionByKey(String idempotencyKey) {
        var grpcRequest = com.example.akka.payments.api.CaptureTransactionRequest.newBuilder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        var grpcResponse = transactionClient.captureTransaction().invoke(grpcRequest);
        return new TransactionModels.CaptureTransactionResponse(grpcResponse.getResult().name());
    }

    @Get("/accounts/{accountId}/transactions")
    public TransactionModels.TransactionsByAccountResponse getTransactionsByAccount(String accountId) {
        var grpcRequest = com.example.akka.payments.api.GetTransactionsByAccountRequest.newBuilder()
                .setAccountId(accountId)
                .build();

        var grpcResponse = transactionClient.getTransactionsByAccount().invoke(grpcRequest);

        var transactions = grpcResponse.getTransactionsList().stream()
                .map(t -> new TransactionModels.TransactionSummary(
                        t.getIdempotencyKey(),
                        t.getTransactionId(),
                        t.getAccountId(),
                        t.getAuthResult(),
                        t.getAuthStatus()
                ))
                .toList();

        return new TransactionModels.TransactionsByAccountResponse(transactions);
    }
}