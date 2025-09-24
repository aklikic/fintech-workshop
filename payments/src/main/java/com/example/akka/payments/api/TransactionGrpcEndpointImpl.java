package com.example.akka.payments.api;

import akka.grpc.GrpcServiceException;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.GrpcEndpoint;
import akka.javasdk.client.ComponentClient;
import com.example.akka.account.api.CaptureTransactionRequest;
import com.example.akka.account.api.CaptureTransactionResponse;
import com.example.akka.payments.application.TransactionWorkflow;
import com.example.akka.payments.domain.TransactionState;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@GrpcEndpoint
public class TransactionGrpcEndpointImpl implements TransactionGrpcEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(TransactionGrpcEndpointImpl.class);
    private final ComponentClient componentClient;

    public TransactionGrpcEndpointImpl(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Override
    public StartTransactionResponse startTransaction(StartTransactionRequest request) {
        logger.info("Starting transaction workflow for idempotency key: {}", request.getIdempotencyKey());
        
        try {
            var workflowRequest = new TransactionWorkflow.AuthorizeTransactionRequest(
                request.getIdempotencyKey(),
                request.getTransactionId(),
                request.getCardPan(),
                request.getCardExpiryDate(),
                request.getCardCvv(),
                request.getAmount(),
                request.getCurrency()
            );
            
            var result = componentClient
                .forWorkflow(request.getIdempotencyKey())
                .method(TransactionWorkflow::authorizeTransaction)
                .invoke(workflowRequest);
            
            return StartTransactionResponse.newBuilder()
                .setResult(mapWorkflowResultToProtoResult(result))
                .build();
                
        } catch (Exception e) {
            logger.error("Failed to start transaction workflow for key: {}", request.getIdempotencyKey(), e);
            return StartTransactionResponse.newBuilder()
                .setResult(StartTransactionResult.FAILED)
                .build();
        }
    }

    @Override
    public Transaction getTransaction(GetTransactionRequest request) {
        logger.info("Getting transaction for idempotency key: {}", request.getIdempotencyKey());
        
        try {
            var state = componentClient
                .forWorkflow(request.getIdempotencyKey())
                .method(TransactionWorkflow::getTransaction)
                .invoke();
            
            return toProtoTransactionState(state);
            
        } catch (Exception e) {
            logger.error("Failed to get transaction for key: {}", request.getIdempotencyKey(), e);
            throw new GrpcServiceException(Status.NOT_FOUND.augmentDescription("Transaction not found: " + e.getMessage()));
        }
    }

    @Override
    public StartCaptureTransactionResponse captureTransaction(StartCaptureTransactionRequest request) {
        logger.info("Capturing transaction for idempotency key: {}", request.getIdempotencyKey());

        try {
            var result = componentClient
                    .forWorkflow(request.getIdempotencyKey())
                    .method(TransactionWorkflow::captureTransaction)
                    .invoke();

            return StartCaptureTransactionResponse.newBuilder()
                    .setResult(mapCaptureResultToProtoResult(result))
                    .build();

        } catch (Exception e) {
            logger.error("Failed to capture transaction for key: {}", request.getIdempotencyKey(), e);
            return StartCaptureTransactionResponse.newBuilder()
                    .setResult(StartCaptureTransactionResult.START_CAPTURE_TRANSACTION_NOT_FOUND)
                    .build();
        }
    }

    @Override
    public StartCancelTransactionResponse cancelTransaction(StartCancelTransactionRequest request) {
        logger.info("Canceling transaction for idempotency key: {}", request.getIdempotencyKey());

        try {
            var result = componentClient
                    .forWorkflow(request.getIdempotencyKey())
                    .method(TransactionWorkflow::cancelTransaction)
                    .invoke();

            return StartCancelTransactionResponse.newBuilder()
                    .setResult(mapCancelResultToProtoResult(result))
                    .build();

        } catch (Exception e) {
            logger.error("Failed to cancel transaction for key: {}", request.getIdempotencyKey(), e);
            return StartCancelTransactionResponse.newBuilder()
                    .setResult(StartCancelTransactionResult.CANCEL_START_TRANSACTION_NOT_FOUND)
                    .build();
        }
    }

    
    private Transaction toProtoTransactionState(TransactionState state) {
        return Transaction.newBuilder()
            .setIdempotencyKey(state.idempotencyKey())
            .setTransactionId(state.transactionId())
            .setCardPan(state.cardData().cardPan())
            .setCardExpiryDate(state.cardData().cardExpiryDate())
            .setCardCvv(state.cardData().cardCvv())
            .setAmount(state.cardData().amount())
            .setCurrency(state.cardData().currency())
            .setAuthCode(state.authCode())
            .setAuthResult(toProtoAuthResult(state.authResult()))
            .setAuthStatus(toProtoAuthStatus(state.authStatus()))
            .setCaptureResult(toProtoCaptureResult(state.captureResult()))
            .setCaptureStatus(toProtoCaptureStatus(state.captureStatus()))
            .setCancelResult(toProtoCancelResult(state.cancelResult()))
            .setCancelStatus(toProtoCancelStatus(state.cancelStatus()))
            .build();
    }
    
    private TransactionAuthResult toProtoAuthResult(TransactionState.AuthResult result) {
        return switch (result) {
            case authorised -> TransactionAuthResult.AUTHORISED;
            case declined -> TransactionAuthResult.DECLINED;
        };
    }
    
    private TransactionAuthStatus toProtoAuthStatus(TransactionState.AuthStatus status) {
        return switch (status) {
            case ok -> TransactionAuthStatus.OK;
            case card_not_found -> TransactionAuthStatus.CARD_NOT_FOUND;
            case insufficient_funds -> TransactionAuthStatus.INSUFFICIENT_FUNDS;
            case account_closed -> TransactionAuthStatus.ACCOUNT_CLOSED;
            case account_not_found -> TransactionAuthStatus.ACCOUNT_NOT_FOUND;
            default -> TransactionAuthStatus.UNDISCLOSED;
        };
    }

    private TransactionCaptureResult toProtoCaptureResult(TransactionState.CaptureResult result) {
        return switch (result) {
            case captured -> TransactionCaptureResult.CAPTURED;
            case declined -> TransactionCaptureResult.CAPTURE_DECLINED;
        };
    }

    private TransactionCaptureStatus toProtoCaptureStatus(TransactionState.CaptureStatus status) {
        return switch (status) {
            case ok -> TransactionCaptureStatus.CAPTURE_OK;
            case account_not_found -> TransactionCaptureStatus.CAPTURE_ACCOUNT_NOT_FOUND;
            case transaction_not_found -> TransactionCaptureStatus.CAPTURE_TRANSACTION_NOT_FOUND;
            default -> TransactionCaptureStatus.CAPTURE_UNDISCLOSED;
        };
    }

    private TransactionCancelResult toProtoCancelResult(TransactionState.CancelResult result) {
        return switch (result) {
            case canceled -> TransactionCancelResult.CANCELED;
            case declined -> TransactionCancelResult.CANCEL_DECLINED;
        };
    }

    private TransactionCancelStatus toProtoCancelStatus(TransactionState.CancelStatus status) {
        return switch (status) {
            case ok -> TransactionCancelStatus.CANCEL_OK;
            case account_not_found -> TransactionCancelStatus.CANCEL_ACCOUNT_NOT_FOUND;
            case transaction_not_found -> TransactionCancelStatus.CANCEL_TRANSACTION_NOT_FOUND;
            default -> TransactionCancelStatus.CANCEL_UNDISCLOSED;
        };
    }


    private StartTransactionResult mapWorkflowResultToProtoResult(TransactionWorkflow.StartAuthorizeTransactionResult result) {
        return switch (result) {
            case STARTED -> StartTransactionResult.STARTED;
            case ALREADY_EXISTS -> StartTransactionResult.ALREADY_EXISTS;
        };
    }
    
    private StartCaptureTransactionResult mapCaptureResultToProtoResult(TransactionWorkflow.StartCaptureTransactionResult result) {
        return switch (result) {
            case CAPTURE_STARTED -> StartCaptureTransactionResult.START_CAPTURE_STARTED;
            case TRANSACTION_NOT_FOUND -> StartCaptureTransactionResult.START_CAPTURE_TRANSACTION_NOT_FOUND;
            case NOT_AUTHORIZED -> StartCaptureTransactionResult.START_CAPTURE_NOT_AUTHORIZED;
            case ALREADY_CAPTURED -> StartCaptureTransactionResult.START_CAPTURE_ALREADY_CAPTURED;
            case ALREADY_CANCELED -> StartCaptureTransactionResult.START_CAPTURE_ALREADY_CANCELED;
        };
    }

    private StartCancelTransactionResult mapCancelResultToProtoResult(TransactionWorkflow.StartCancelTransactionResult result) {
        return switch (result) {
            case CANCEL_STARTED -> StartCancelTransactionResult.CANCEL_START_STARTED;
            case TRANSACTION_NOT_FOUND -> StartCancelTransactionResult.CANCEL_START_TRANSACTION_NOT_FOUND;
            case NOT_AUTHORIZED -> StartCancelTransactionResult.CANCEL_START_NOT_AUTHORIZED;
            case ALREADY_CAPTURED -> StartCancelTransactionResult.CANCEL_START_ALREADY_CAPTURED;
            case ALREADY_CANCELED -> StartCancelTransactionResult.CANCEL_START_ALREADY_CANCELED;
        };
    }

    @Override
    public GetTransactionsByAccountResponse getTransactionsByAccount(GetTransactionsByAccountRequest request) {
        logger.info("Getting transactions for account ID: {}", request.getAccountId());

        try {
            var result = componentClient
                .forView()
                .method(com.example.akka.payments.application.TransactionsByAccountView::getTransactionsByAccount)
                .invoke(request.getAccountId());

            var transactionSummaries = result.transactions().stream()
                .map(transaction -> TransactionSummary.newBuilder()
                    .setIdempotencyKey(transaction.idempotencyKey())
                    .setTransactionId(transaction.transactionId())
                    .setAccountId(transaction.accountId())
                    .setAuthResult(transaction.authResult())
                    .setAuthStatus(transaction.authStatus())
                    .setCaptureResult(transaction.captureResult())
                    .setCaptureStatus(transaction.captureStatus())
                    .setCancelResult(transaction.cancelResult())
                    .setCancelStatus(transaction.cancelStatus())
                    .build())
                .toList();

            return GetTransactionsByAccountResponse.newBuilder()
                .addAllTransactions(transactionSummaries)
                .build();

        } catch (Exception e) {
            logger.error("Failed to get transactions for account: {}", request.getAccountId(), e);
            return GetTransactionsByAccountResponse.newBuilder().build();
        }
    }
}