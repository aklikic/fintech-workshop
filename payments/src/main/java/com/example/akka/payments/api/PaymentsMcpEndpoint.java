package com.example.akka.payments.api;

import akka.javasdk.JsonSupport;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.mcp.McpEndpoint;
import akka.javasdk.annotations.mcp.McpTool;
import akka.javasdk.client.ComponentClient;
import com.example.akka.payments.application.CardEntity;
import com.example.akka.payments.application.TransactionWorkflow;
import com.example.akka.payments.application.TransactionsByAccountView;
import com.example.akka.payments.domain.TransactionState;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@McpEndpoint(serverName = "account-mcp", serverVersion = "0.0.1")
public class PaymentsMcpEndpoint {

    private final ComponentClient componentClient;

    public PaymentsMcpEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }


    @McpTool(description = "Create a new card")
    public String createCard ( @Description("Information for creating a new card.") Card card) {
        componentClient.forEventSourcedEntity(card.pan()).method(CardEntity::createCard).invoke(new CardEntity.ApiCard(card.pan(), card.expiryDate(), card.cvv(), card.accountId()));
        return "OK";
    }

    @McpTool(description = "Get card by pan")
    public String getCard(@Description("Card pan")String pan) {
       try {
           var result = componentClient.forEventSourcedEntity(pan).method(CardEntity::getCard).invoke();
           var response = new Card(
                   result.pan(),
                   result.expiryDate(),
                   result.cvv(),
                   result.accountId()
           );
           return JsonSupport.encodeToString(response);
       }catch (Exception e) {
           return e.getMessage();
       }
    }

    @McpTool(description = "Start/authorise transaction")
    public String startTransaction(@Description("Information needed for to start/authorise transaction")StartTransactionRequest request) {
       var startTransactionRequest =  new TransactionWorkflow.AuthorizeTransactionRequest(
               request.idempotencyKey(),
               request.transactionId(),
               request.cardPan(),
               request.cardExpiryDate(),
               request.cardCvv(),
               request.amount(),
               request.currency()
       );
       var response = componentClient.forWorkflow(request.idempotencyKey()).method(TransactionWorkflow::authorizeTransaction).invoke(startTransactionRequest);
       return response.name();
    }

    @McpTool(description = "Get transaction by idempotencyKey")
    public String getTransaction(@Description("Transaction idempotencyKey")String idempotencyKey) {
        try {
            var result = componentClient.forWorkflow(idempotencyKey).method(TransactionWorkflow::getTransaction).invoke();

            var captureResult = "N/A";
            var captureStatus = "N/A";
            if(!(result.captureResult() == TransactionState.CaptureResult.declined && result.captureStatus() == TransactionState.CaptureStatus.ok)){
                captureResult = result.captureResult().name();
                captureStatus = result.captureStatus().name();
            }
            var cancelResult = "N/A";
            var cancelStatus = "N/A";
            if(!(result.cancelResult() == TransactionState.CancelResult.declined && result.cancelStatus() == TransactionState.CancelStatus.ok)){
                cancelResult = result.cancelResult().name();
                cancelStatus = result.cancelStatus().name();
            }
            var response = new Transaction(
                    result.idempotencyKey(),
                    result.transactionId(),
                    result.cardData().cardPan(),
                    result.cardData().cardExpiryDate(),
                    result.cardData().cardCvv(),
                    result.cardData().amount(),
                    result.cardData().currency(),
                    result.authCode(),
                    result.authResult().name(),
                    result.authStatus().name(),
                    captureResult,
                    captureStatus,
                    cancelResult,
                    cancelStatus
            );
            return JsonSupport.encodeToString(response);
        }catch (Exception e) {
            return e.getMessage();
        }
    }

    @McpTool(description = "Capture authorised transaction by transaction idempotencyKey")
    public String captureTransactionByKey(@Description("Transaction idempotencyKey")String idempotencyKey) {
        var result = componentClient.forWorkflow(idempotencyKey).method(TransactionWorkflow::captureTransaction).invoke();
        return result.name();
    }

    @McpTool(description = "Cancel authorised transaction by transaction idempotencyKey")
    public String cancelTransactionByKey(@Description("Transaction idempotencyKey")String idempotencyKey) {
        var result = componentClient.forWorkflow(idempotencyKey).method(TransactionWorkflow::cancelTransaction).invoke();
        return result.name();
    }

    @McpTool(description = "Get all transactions for account")
    public String getTransactionsByAccount(@Description("Account id")String accountId) {
        var result = componentClient.forView().method(TransactionsByAccountView::getTransactionsByAccount).invoke(accountId);

        var transactions = result.transactions().stream()
                .map(t -> new TransactionSummary(
                        t.idempotencyKey(),
                        t.transactionId(),
                        t.accountId(),
                        t.authResult(),
                        t.authStatus(),
                        t.captureResult(),
                        t.captureStatus(),
                        t.cancelResult(),
                        t.cancelStatus()
                ))
                .toList();

        return JsonSupport.encodeToString(new TransactionsByAccountResponse(transactions));
    }

    public record Card(String pan, String expiryDate, String cvv, String accountId) {}

    public record StartTransactionRequest(
            @Description("requester unique id. Recommended using UUID") String idempotencyKey,
            @Description("unique transaction id. it can also be the same value as idempotencyKey. Recommended using UUID")String transactionId,
            @Description("Card pan")String cardPan,
            @Description("Card expiry date in format MM/YY")String cardExpiryDate,
            @Description("Card CVV. 3 digit number")String cardCvv,
            @Description("Transaction amount in cents") int amount,
            @Description("Transaction amount currency. 3 letter code")String currency) {}

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
