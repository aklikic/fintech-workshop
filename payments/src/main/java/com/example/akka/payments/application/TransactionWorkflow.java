package com.example.akka.payments.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import com.example.akka.account.api.*;
import com.example.akka.payments.domain.TransactionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;

@ComponentId("transaction-workflow")
public class TransactionWorkflow extends Workflow<TransactionState> {
    
    private static final Logger logger = LoggerFactory.getLogger(TransactionWorkflow.class);
    private final ComponentClient componentClient;
    private final AccountGrpcEndpointClient accountClient;
    
    public TransactionWorkflow(ComponentClient componentClient, AccountGrpcEndpointClient accountClient) {
        this.componentClient = componentClient;
        this.accountClient = accountClient;
    }

    @Override
    public TransactionState emptyState() {
        return TransactionState.empty();
    }

    public Effect<StartAuthorizeTransactionResult> authorizeTransaction(AuthorizeTransactionRequest request) {
        logger.info("Starting transaction workflow for idempotency key: {}", request.idempotencyKey());

        if (currentState() != null && !currentState().isEmpty()) {
            return effects().reply(StartAuthorizeTransactionResult.ALREADY_EXISTS);
        }


        var cardData = new TransactionState.CardData(
                request.cardPan(),
                request.cardExpiryDate(),
                request.cardCvv(),
                request.amount(),
                request.currency()
        );

        var initialState = currentState().init(request.idempotencyKey(), request.transactionId(), cardData);

        return effects()
                .updateState(initialState)
                .transitionTo(TransactionWorkflow::validateCardStep)
                .thenReply(StartAuthorizeTransactionResult.STARTED);
    }

    public Effect<StartCaptureTransactionResult> captureTransaction() {
        logger.info("Capture transaction requested for transaction: {}",
                currentState() != null ? currentState().transactionId() : "unknown");

        logger.info("state:{}",currentState());

        if (currentState() == null || currentState().isEmpty()) {
            return effects().reply(StartCaptureTransactionResult.TRANSACTION_NOT_FOUND);
        }

        if (currentState().authResult() != TransactionState.AuthResult.authorised) {
            return effects().reply(StartCaptureTransactionResult.NOT_AUTHORIZED);
        }

        if (currentState().captureResult() == TransactionState.CaptureResult.captured) {
            return effects().reply(StartCaptureTransactionResult.ALREADY_CAPTURED);
        }

        if (currentState().cancelResult() == TransactionState.CancelResult.canceled) {
            return effects().reply(StartCaptureTransactionResult.ALREADY_CANCELED);
        }

        return effects()
                .transitionTo(TransactionWorkflow::captureTransactionStep)
                .thenReply(StartCaptureTransactionResult.CAPTURE_STARTED);
    }

    public Effect<StartCancelTransactionResult> cancelTransaction() {
        logger.info("Cancel transaction requested for transaction: {}",
                currentState() != null ? currentState().transactionId() : "unknown");

        if (currentState() == null || currentState().isEmpty()) {
            return effects().reply(StartCancelTransactionResult.TRANSACTION_NOT_FOUND);
        }

        if (currentState().authResult() != TransactionState.AuthResult.authorised) {
            return effects().reply(StartCancelTransactionResult.NOT_AUTHORIZED);
        }

        if (currentState().captureResult() == TransactionState.CaptureResult.captured) {
            return effects().reply(StartCancelTransactionResult.ALREADY_CAPTURED);
        }
        if (currentState().cancelResult() == TransactionState.CancelResult.canceled) {
            return effects().reply(StartCancelTransactionResult.ALREADY_CANCELED);
        }

        return effects()
                .transitionTo(TransactionWorkflow::cancelTransactionStep)
                .thenReply(StartCancelTransactionResult.CANCEL_STARTED);
    }

    public ReadOnlyEffect<TransactionState> getTransaction() {
        if (currentState() == null || currentState().isEmpty()) {
            return effects().error("Transaction not found");
        }
        return effects().reply(currentState());
    }

    @Override
    public WorkflowSettings settings() {
        return WorkflowSettings.builder()
                .defaultStepTimeout(Duration.ofMinutes(5))
                .build();
    }

    private StepEffect validateCardStep() {
        logger.info("Validating card for transaction: {}", currentState().transactionId());
        Optional<String> accountId = Optional.empty();
        try {
            var card = componentClient
                    .forEventSourcedEntity(currentState().cardData().cardPan())
                    .method(CardEntity::getCard)
                    .invoke();

            if(!card.isEmpty() && card.expiryDate().equals(currentState().cardData().cardExpiryDate())
                    && card.cvv().equals(currentState().cardData().cardCvv())){
                accountId = Optional.of(card.accountId());
            }

        } catch (Exception e) {
            logger.error("Card validation failed for transaction: {}", currentState().transactionId(), e);
        }

        if (accountId.isEmpty()) {
            logger.info("Card validation failed for transaction: {}", currentState().transactionId());
            var updatedState = currentState().withAuthResult(
                    "",
                    TransactionState.AuthResult.declined,
                    TransactionState.AuthStatus.card_not_found
            );
            return stepEffects()
                    .updateState(updatedState)
                    .thenEnd();
        }else{
            logger.info("Card validation successful for transaction: {}", currentState().transactionId());
            var updatedState = currentState().withCardValid(accountId.get());
            return stepEffects()
                    .updateState(updatedState)
                    .thenTransitionTo(TransactionWorkflow::authorizeTransactionStep);
        }
    }

    private StepEffect authorizeTransactionStep() {
        logger.info("Authorizing transaction: {}", currentState().transactionId());
        var authResult = TransactionState.AuthResult.declined;
        var authStatus = TransactionState.AuthStatus.undiscosed;
        var authCode = "N/A";
        try {
            // Authorize transaction with the account service
            var authRequest = com.example.akka.account.api.AuthorizeTransactionRequest.newBuilder()
                    .setAccountId(currentState().accountId())
                    .setTransactionId(currentState().transactionId())
                    .setAmount(currentState().cardData().amount())
                    .build();

            var protoResponse = accountClient.authorizeTransaction().invoke(authRequest);
            authResult = mapProtoAuthResult(protoResponse.getAuthResult());
            authStatus = mapProtoAuthStatus(protoResponse.getAuthStatus());
            authCode = protoResponse.getAuthCode();
        } catch (Exception e) {
            logger.error("Authorization failed for transaction: {}", currentState().transactionId(), e);
        }

        logger.info("Authorization result for transaction {}: {} - {}",
                currentState().transactionId(), authResult, authStatus);

        var updatedState = currentState().withAuthResult( authCode, authResult, authStatus);

        // If authorization failed, end the workflow
        if (authResult == TransactionState.AuthResult.declined) {
            return stepEffects().updateState(updatedState).thenEnd();
        }

        //create timer for timeout
        var deferredCall = componentClient
                .forWorkflow(commandContext().workflowId())
                .method(TransactionWorkflow::cancelTransaction)
                .deferred();
        timers().createSingleTimer(scheduleCaptureTimeoutTimerId(), Duration.ofMinutes(5), deferredCall);

        // If authorized successfully, pause and wait for external capture trigger
        return stepEffects()
                .updateState(updatedState)
                .thenPause();
    }

    private String scheduleCaptureTimeoutTimerId() {
        return "capture-timeout-scheduler-" + commandContext().workflowId();
    }

    private StepEffect captureTransactionStep() {
        logger.info("Capturing transaction: {}", currentState().transactionId());
        var captureResult = TransactionState.CaptureResult.declined;
        var captureStatus = TransactionState.CaptureStatus.undiscosed;
        try {
            // Use stored accountId from authorization step
            var captureRequest = CaptureTransactionRequest.newBuilder()
                    .setAccountId(currentState().accountId())
                    .setTransactionId(currentState().transactionId())
                    .build();

            var response = accountClient.captureTransaction().invoke(captureRequest);
            captureResult =  mapProtoCaptureResult(response.getCaptureResult());
            captureStatus = mapProtoCaptureStatus(response.getCaptureStatus());
        } catch (Exception e) {
            logger.error("Capture failed for transaction: {}", currentState().transactionId(), e);
        }
        //delete timeout timer
        timers().delete(scheduleCaptureTimeoutTimerId());
        logger.info("Capture result for transaction {}: {}", currentState().transactionId(), captureResult);

        var updatedState = currentState().withCaptured(captureResult, captureStatus);
        return stepEffects()
                .updateState(updatedState)
                .thenEnd();
    }
    private StepEffect cancelTransactionStep() {
        logger.info("Cancel transaction: {}", currentState().transactionId());
        var cancelResult = TransactionState.CancelResult.declined;
        var cancelStatus = TransactionState.CancelStatus.undiscosed;
        try {
            // Use stored accountId from authorization step
            var cancelRequest = CancelTransactionRequest.newBuilder()
                    .setAccountId(currentState().accountId())
                    .setTransactionId(currentState().transactionId())
                    .build();

            var response = accountClient.cancelTransaction().invoke(cancelRequest);
            cancelResult =  mapProtoCancelResult(response.getCancelResult());
            cancelStatus =  mapProtoCancelStatus(response.getCancelStatus());
        } catch (Exception e) {
            logger.error("Capture failed for transaction: {}", currentState().transactionId(), e);
        }
        //delete timeout timer
        timers().delete(scheduleCaptureTimeoutTimerId());
        logger.info("Cancel result for transaction {}: {}", currentState().transactionId(), cancelResult);

        var updatedState = currentState().withCanceled(cancelResult,cancelStatus);
        return stepEffects()
                .updateState(updatedState)
                .thenEnd();
    }
    
    private TransactionState.AuthResult mapProtoAuthResult(AuthResult protoResult) {
        return switch (protoResult) {
            case AUTHORISED -> TransactionState.AuthResult.authorised;
            default -> TransactionState.AuthResult.declined;
        };
    }
    
    private TransactionState.AuthStatus mapProtoAuthStatus(AuthStatus protoStatus) {
        return switch (protoStatus) {
            case OK -> TransactionState.AuthStatus.ok;
            case CARD_NOT_FOUND -> TransactionState.AuthStatus.card_not_found;
            case INSUFFICIENT_FUNDS -> TransactionState.AuthStatus.insufficient_funds;
            case ACCOUNT_CLOSED -> TransactionState.AuthStatus.account_closed;
            case ACCOUNT_NOT_FOUND -> TransactionState.AuthStatus.account_not_found;
            default -> TransactionState.AuthStatus.undiscosed;
        };
    }
    
    public record AuthorizeTransactionRequest(
        String idempotencyKey,
        String transactionId,
        String cardPan,
        String cardExpiryDate,
        String cardCvv,
        int amount,
        String currency
    ) {}

    
    public enum StartAuthorizeTransactionResult {
        STARTED,
        ALREADY_EXISTS
    }
    
    public enum StartCaptureTransactionResult {
        CAPTURE_STARTED,
        TRANSACTION_NOT_FOUND,
        NOT_AUTHORIZED,
        ALREADY_CAPTURED,
        ALREADY_CANCELED
    }

    private TransactionState.CaptureResult mapProtoCaptureResult(CaptureTransResult protoResult) {
        return switch (protoResult) {
            case CAPTURED -> TransactionState.CaptureResult.captured;
            default -> TransactionState.CaptureResult.declined;
        };
    }

    private TransactionState.CaptureStatus mapProtoCaptureStatus(CaptureTransStatus protoStatus) {
        return switch (protoStatus) {
            case CAPTURE_OK -> TransactionState.CaptureStatus.ok;
            case CAPTURE_ACCOUNT_NOT_FOUND ->   TransactionState.CaptureStatus.account_not_found;
            case CAPTURE_TRANSACTION_NOT_FOUND ->    TransactionState.CaptureStatus.transaction_not_found;
            default -> TransactionState.CaptureStatus.undiscosed;
        };
    }

    public enum StartCancelTransactionResult {
        CANCEL_STARTED,
        TRANSACTION_NOT_FOUND,
        NOT_AUTHORIZED,
        ALREADY_CAPTURED,
        ALREADY_CANCELED
    }

    private TransactionState.CancelResult mapProtoCancelResult(CancelTransResult protoResult) {
        return switch (protoResult) {
            case CANCELED -> TransactionState.CancelResult.canceled;
            default -> TransactionState.CancelResult.declined;
        };
    }

    private TransactionState.CancelStatus mapProtoCancelStatus(CancelTransStatus protoStatus) {
        return switch (protoStatus) {
            case CANCEL_OK -> TransactionState.CancelStatus.ok;
            case CANCEL_ACCOUNT_NOT_FOUND ->   TransactionState.CancelStatus.account_not_found;
            case CANCEL_TRANSACTION_NOT_FOUND ->    TransactionState.CancelStatus.transaction_not_found;
            default -> TransactionState.CancelStatus.undiscosed;
        };
    }
}