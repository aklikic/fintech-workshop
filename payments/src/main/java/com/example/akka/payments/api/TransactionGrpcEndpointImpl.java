package com.example.akka.payments.api;

import akka.grpc.GrpcServiceException;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.GrpcEndpoint;
import akka.javasdk.client.ComponentClient;
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
            var workflowRequest = new TransactionWorkflow.StartTransactionRequest(
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
                .method(TransactionWorkflow::startTransaction)
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
    public CaptureTransactionResponse captureTransaction(CaptureTransactionRequest request) {
        logger.info("Capturing transaction for idempotency key: {}", request.getIdempotencyKey());
        
        try {
            var result = componentClient
                .forWorkflow(request.getIdempotencyKey())
                .method(TransactionWorkflow::captureTransaction)
                .invoke();
            
            return CaptureTransactionResponse.newBuilder()
                .setResult(mapCaptureResultToProtoResult(result))
                .build();
                
        } catch (Exception e) {
            logger.error("Failed to capture transaction for key: {}", request.getIdempotencyKey(), e);
            return CaptureTransactionResponse.newBuilder()
                .setResult(CaptureTransactionResult.TRANSACTION_NOT_FOUND)
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
            .setCaptured(state.captured())
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
            case undiscosed -> TransactionAuthStatus.UNDISCLOSED;
            case account_not_found -> TransactionAuthStatus.ACCOUNT_NOT_FOUND;
        };
    }
    
    private StartTransactionResult mapWorkflowResultToProtoResult(TransactionWorkflow.StartTransactionResult result) {
        return switch (result) {
            case STARTED -> StartTransactionResult.STARTED;
            case ALREADY_EXISTS -> StartTransactionResult.ALREADY_EXISTS;
        };
    }
    
    private CaptureTransactionResult mapCaptureResultToProtoResult(TransactionWorkflow.CaptureTransactionResult result) {
        return switch (result) {
            case CAPTURE_STARTED -> CaptureTransactionResult.CAPTURE_STARTED;
            case TRANSACTION_NOT_FOUND -> CaptureTransactionResult.TRANSACTION_NOT_FOUND;
            case NOT_AUTHORIZED -> CaptureTransactionResult.NOT_AUTHORIZED;
            case ALREADY_CAPTURED -> CaptureTransactionResult.ALREADY_CAPTURED;
        };
    }
}