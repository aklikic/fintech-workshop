package com.example.akka.payments.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import com.example.akka.payments.application.TransactionWorkflow;
import com.example.akka.payments.domain.TransactionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@HttpEndpoint("/transactions")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class TransactionHttpEndpoint extends AbstractHttpEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(TransactionHttpEndpoint.class);
    private final ComponentClient componentClient;

    public TransactionHttpEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // Request/Response records
    public record StartTransactionRequest(
            String idempotencyKey,
            String transactionId,
            String cardPan,
            String cardExpiryDate,
            String cardCvv,
            int amount,
            String currency) {}

    public enum StartTransactionResult {
        STARTED, ALREADY_EXISTS, FAILED
    }

    public record StartTransactionResponse(StartTransactionResult result) {}

    public record TransactionResponse(
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

    public enum CaptureTransactionResult {
        START_CAPTURE_STARTED, START_CAPTURE_TRANSACTION_NOT_FOUND, START_CAPTURE_NOT_AUTHORIZED,
        START_CAPTURE_ALREADY_CAPTURED, START_CAPTURE_ALREADY_CANCELED
    }

    public record CaptureTransactionResponse(CaptureTransactionResult result) {}

    public enum CancelTransactionResult {
        CANCEL_START_STARTED, CANCEL_START_TRANSACTION_NOT_FOUND, CANCEL_START_NOT_AUTHORIZED,
        CANCEL_START_ALREADY_CAPTURED, CANCEL_START_ALREADY_CANCELED
    }

    public record CancelTransactionResponse(CancelTransactionResult result) {}

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

    public record GetTransactionsByAccountResponse(List<TransactionSummary> transactions) {}

    /**
     * Starts a new transaction workflow.
     * POST /transactions
     */
    @Post
    public StartTransactionResponse startTransaction(StartTransactionRequest request) {
        logger.info("Starting transaction workflow for idempotency key: {}", request.idempotencyKey());

        try {
            var workflowRequest = new TransactionWorkflow.AuthorizeTransactionRequest(
                    request.idempotencyKey(),
                    request.transactionId(),
                    request.cardPan(),
                    request.cardExpiryDate(),
                    request.cardCvv(),
                    request.amount(),
                    request.currency()
            );

            var result = componentClient
                    .forWorkflow(request.idempotencyKey())
                    .method(TransactionWorkflow::authorizeTransaction)
                    .invoke(workflowRequest);

            return new StartTransactionResponse(mapWorkflowResultToHttpResult(result));

        } catch (Exception e) {
            logger.error("Failed to start transaction workflow for key: {}", request.idempotencyKey(), e);
            return new StartTransactionResponse(StartTransactionResult.FAILED);
        }
    }

    /**
     * Gets a transaction by idempotency key.
     * GET /transactions/{idempotencyKey}
     */
    @Get("/{idempotencyKey}")
    public TransactionResponse getTransaction(String idempotencyKey) {
        logger.info("Getting transaction for idempotency key: {}", idempotencyKey);

        try {
            var state = componentClient
                    .forWorkflow(idempotencyKey)
                    .method(TransactionWorkflow::getTransaction)
                    .invoke();

            return fromTransactionState(state);

        } catch (Exception e) {
            logger.error("Failed to get transaction for key: {}", idempotencyKey, e);
            throw new RuntimeException("Transaction not found: " + e.getMessage());
        }
    }

    /**
     * Captures a transaction by idempotency key.
     * POST /transactions/{idempotencyKey}/capture
     */
    @Post("/{idempotencyKey}/capture")
    public CaptureTransactionResponse captureTransaction(String idempotencyKey) {
        logger.info("Capturing transaction for idempotency key: {}", idempotencyKey);

        try {
            var result = componentClient
                    .forWorkflow(idempotencyKey)
                    .method(TransactionWorkflow::captureTransaction)
                    .invoke();

            return new CaptureTransactionResponse(mapCaptureResultToHttpResult(result));

        } catch (Exception e) {
            logger.error("Failed to capture transaction for key: {}", idempotencyKey, e);
            return new CaptureTransactionResponse(CaptureTransactionResult.START_CAPTURE_TRANSACTION_NOT_FOUND);
        }
    }

    /**
     * Cancels a transaction by idempotency key.
     * POST /transactions/{idempotencyKey}/cancel
     */
    @Post("/{idempotencyKey}/cancel")
    public CancelTransactionResponse cancelTransaction(String idempotencyKey) {
        logger.info("Canceling transaction for idempotency key: {}", idempotencyKey);

        try {
            var result = componentClient
                    .forWorkflow(idempotencyKey)
                    .method(TransactionWorkflow::cancelTransaction)
                    .invoke();

            return new CancelTransactionResponse(mapCancelResultToHttpResult(result));

        } catch (Exception e) {
            logger.error("Failed to cancel transaction for key: {}", idempotencyKey, e);
            return new CancelTransactionResponse(CancelTransactionResult.CANCEL_START_TRANSACTION_NOT_FOUND);
        }
    }

    /**
     * Gets all transactions for a specific account.
     * GET /transactions/by-account/{accountId}
     */
    @Get("/by-account/{accountId}")
    public GetTransactionsByAccountResponse getTransactionsByAccount(String accountId) {
        logger.info("Getting transactions for account ID: {}", accountId);

        try {
            var result = componentClient
                    .forView()
                    .method(com.example.akka.payments.application.TransactionsByAccountView::getTransactionsByAccount)
                    .invoke(accountId);

            var transactionSummaries = result.transactions().stream()
                    .map(transaction -> new TransactionSummary(
                            transaction.idempotencyKey(),
                            transaction.transactionId(),
                            transaction.accountId(),
                            transaction.authResult(),
                            transaction.authStatus(),
                            transaction.captureResult(),
                            transaction.captureStatus(),
                            transaction.cancelResult(),
                            transaction.cancelStatus()))
                    .toList();

            return new GetTransactionsByAccountResponse(transactionSummaries);

        } catch (Exception e) {
            logger.error("Failed to get transactions for account: {}", accountId, e);
            return new GetTransactionsByAccountResponse(List.of());
        }
    }

    private TransactionResponse fromTransactionState(TransactionState state) {
        return new TransactionResponse(
                state.idempotencyKey(),
                state.transactionId(),
                state.cardData().cardPan(),
                state.cardData().cardExpiryDate(),
                state.cardData().cardCvv(),
                state.cardData().amount(),
                state.cardData().currency(),
                state.authCode(),
                state.authResult().toString(),
                state.authStatus().toString(),
                state.captureResult().toString(),
                state.captureStatus().toString(),
                state.cancelResult().toString(),
                state.cancelStatus().toString()
        );
    }

    private StartTransactionResult mapWorkflowResultToHttpResult(TransactionWorkflow.StartAuthorizeTransactionResult result) {
        return switch (result) {
            case STARTED -> StartTransactionResult.STARTED;
            case ALREADY_EXISTS -> StartTransactionResult.ALREADY_EXISTS;
        };
    }

    private CaptureTransactionResult mapCaptureResultToHttpResult(TransactionWorkflow.StartCaptureTransactionResult result) {
        return switch (result) {
            case CAPTURE_STARTED -> CaptureTransactionResult.START_CAPTURE_STARTED;
            case TRANSACTION_NOT_FOUND -> CaptureTransactionResult.START_CAPTURE_TRANSACTION_NOT_FOUND;
            case NOT_AUTHORIZED -> CaptureTransactionResult.START_CAPTURE_NOT_AUTHORIZED;
            case ALREADY_CAPTURED -> CaptureTransactionResult.START_CAPTURE_ALREADY_CAPTURED;
            case ALREADY_CANCELED -> CaptureTransactionResult.START_CAPTURE_ALREADY_CANCELED;
        };
    }

    private CancelTransactionResult mapCancelResultToHttpResult(TransactionWorkflow.StartCancelTransactionResult result) {
        return switch (result) {
            case CANCEL_STARTED -> CancelTransactionResult.CANCEL_START_STARTED;
            case TRANSACTION_NOT_FOUND -> CancelTransactionResult.CANCEL_START_TRANSACTION_NOT_FOUND;
            case NOT_AUTHORIZED -> CancelTransactionResult.CANCEL_START_NOT_AUTHORIZED;
            case ALREADY_CAPTURED -> CancelTransactionResult.CANCEL_START_ALREADY_CAPTURED;
            case ALREADY_CANCELED -> CancelTransactionResult.CANCEL_START_ALREADY_CANCELED;
        };
    }
}