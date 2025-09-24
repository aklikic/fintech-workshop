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

    public Effect<CaptureTransactionResult> captureTransaction() {
        logger.info("Capture transaction requested for transaction: {}",
                currentState() != null ? currentState().transactionId() : "unknown");

        if (currentState() == null || currentState().isEmpty()) {
            return effects().reply(CaptureTransactionResult.TRANSACTION_NOT_FOUND);
        }

        if (currentState().authResult() != TransactionState.AuthResult.authorised) {
            return effects().reply(CaptureTransactionResult.NOT_AUTHORIZED);
        }

        if (currentState().captured()) {
            return effects().reply(CaptureTransactionResult.ALREADY_CAPTURED);
        }

        return effects()
                .transitionTo(TransactionWorkflow::captureTransactionStep)
                .thenReply(CaptureTransactionResult.CAPTURE_STARTED);
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

        // If authorized successfully, pause and wait for external capture trigger
        return stepEffects()
                .updateState(updatedState)
                .thenTransitionTo(TransactionWorkflow::scheduleCapture);
    }

    private StepEffect scheduleCapture() {
        var deferredCall = componentClient
                .forWorkflow(commandContext().workflowId())
                .method(TransactionWorkflow::captureTransaction)
                .deferred();
        timers().createSingleTimer("capture-scheduler-" + commandContext().workflowId(), Duration.ofMinutes(1), deferredCall);
        return stepEffects().thenPause();
    }
    private StepEffect captureTransactionStep() {
        logger.info("Capturing transaction: {}", currentState().transactionId());
        var success = false;
        try {
            // Use stored accountId from authorization step
            var captureRequest = CaptureTransactionRequest.newBuilder()
                    .setAccountId(currentState().accountId())
                    .setTransactionId(currentState().transactionId())
                    .build();

            var response = accountClient.captureTransaction().invoke(captureRequest);
            success = response.getSuccess();
        } catch (Exception e) {
            logger.error("Capture failed for transaction: {}", currentState().transactionId(), e);
        }

        logger.info("Capture result for transaction {}: {}", currentState().transactionId(), success);
        var updatedState = currentState().withCaptured(success);
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
    
    public enum CaptureTransactionResult {
        CAPTURE_STARTED,
        TRANSACTION_NOT_FOUND,
        NOT_AUTHORIZED,
        ALREADY_CAPTURED
    }
}