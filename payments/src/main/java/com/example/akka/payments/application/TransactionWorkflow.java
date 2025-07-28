package com.example.akka.payments.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import com.example.akka.account.api.AccountGrpcEndpointClient;
import com.example.akka.account.api.AuthorizeTransactionRequest;
import com.example.akka.account.api.AuthResult;
import com.example.akka.account.api.AuthStatus;
import com.example.akka.payments.domain.TransactionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

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
    public WorkflowDef<TransactionState> definition() {
        var validateCardStep = step("validate-card")
            .call(() -> {
                logger.info("Validating card for transaction: {}", currentState().transactionId());
                try {
                    var card = componentClient
                        .forEventSourcedEntity(currentState().cardData().cardPan())
                        .method(CardEntity::getCard)
                        .invoke();
                    
                    if (card.isEmpty()) {
                        return false;
                    }
                    
                    var valid = card.expiryDate().equals(currentState().cardData().cardExpiryDate()) 
                        && card.cvv().equals(currentState().cardData().cardCvv());
                    return valid;
                } catch (Exception e) {
                    logger.error("Card validation failed for transaction: {}", currentState().transactionId(), e);
                    return false;
                }
            })
            .andThen(Boolean.class, isValid -> {
                if (!isValid) {
                    logger.info("Card validation failed for transaction: {}", currentState().transactionId());
                    var updatedState = currentState().withAuthResult(
                        "",
                        TransactionState.AuthResult.declined,
                        TransactionState.AuthStatus.card_not_found
                    );
                    return effects().updateState(updatedState).end();
                }
                logger.info("Card validation successful for transaction: {}", currentState().transactionId());
                return effects().transitionTo("authorize-transaction");
            });
            
        var authorizeTransactionStep = step("authorize-transaction")
            .call(() -> {
                logger.info("Authorizing transaction: {}", currentState().transactionId());
                try {
                    // Get the card to find the associated account
                    var card = componentClient
                        .forEventSourcedEntity(currentState().cardData().cardPan())
                        .method(CardEntity::getCard)
                        .invoke();

                    // Authorize transaction with the account service
                    var authRequest = AuthorizeTransactionRequest.newBuilder()
                        .setAccountId(card.accountId())
                        .setTransactionId(currentState().transactionId())
                        .setAmount(currentState().cardData().amount())
                        .build();

                    var protoResponse = accountClient.authorizeTransaction().invoke(authRequest);
                    
                    // Map protobuf response to serializable record
                    return new AuthorizationResult(
                        protoResponse.getAuthCode(),
                        mapProtoAuthResult(protoResponse.getAuthResult()),
                        mapProtoAuthStatus(protoResponse.getAuthStatus())
                    );
                } catch (Exception e) {
                    logger.error("Authorization failed for transaction: {}", currentState().transactionId(), e);
                    return new AuthorizationResult(
                        "",
                        TransactionState.AuthResult.declined,
                        TransactionState.AuthStatus.undiscosed
                    );
                }
            })
            .andThen(AuthorizationResult.class, response -> {
                logger.info("Authorization result for transaction {}: {} - {}",
                           currentState().transactionId(), response.authResult(), response.authStatus());

                var updatedState = currentState().withAuthResult(
                    response.authCode(),
                    response.authResult(),
                    response.authStatus()
                );

                return effects().updateState(updatedState).end();
            });

        return workflow()
            .timeout(Duration.ofMinutes(5))
            .addStep(validateCardStep)
            .addStep(authorizeTransactionStep);
    }
    
    public Effect<StartTransactionResult> startTransaction(StartTransactionRequest request) {
        logger.info("Starting transaction workflow for idempotency key: {}", request.idempotencyKey());
        
        if (currentState() != null && !currentState().isEmpty()) {
            return effects().reply(StartTransactionResult.ALREADY_EXISTS);
        }
        
        var cardData = new TransactionState.CardData(
            request.cardPan(),
            request.cardExpiryDate(),
            request.cardCvv(),
            request.amount(),
            request.currency()
        );
        
        var initialState = new TransactionState(
            request.idempotencyKey(),
            request.transactionId(),
            cardData,
            "",
            TransactionState.AuthResult.declined,
            TransactionState.AuthStatus.ok
        );
        
        return effects()
            .updateState(initialState)
            .transitionTo("validate-card")
            .thenReply(StartTransactionResult.STARTED);
    }
    
    public ReadOnlyEffect<TransactionState> getTransaction() {
        if (currentState() == null || currentState().isEmpty()) {
            return effects().error("Transaction not found");
        }
        return effects().reply(currentState());
    }
    
    private TransactionState.AuthResult mapProtoAuthResult(AuthResult protoResult) {
        return switch (protoResult) {
            case AUTHORISED -> TransactionState.AuthResult.authorised;
            case DECLINED -> TransactionState.AuthResult.declined;
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
            case UNDISCLOSED -> TransactionState.AuthStatus.undiscosed;
            default -> TransactionState.AuthStatus.undiscosed;
        };
    }
    
    public record StartTransactionRequest(
        String idempotencyKey,
        String transactionId,
        String cardPan,
        String cardExpiryDate,
        String cardCvv,
        int amount,
        String currency
    ) {}
    
    public record AuthorizationResult(
        String authCode,
        TransactionState.AuthResult authResult,
        TransactionState.AuthStatus authStatus
    ) {}
    
    public enum StartTransactionResult {
        STARTED,
        ALREADY_EXISTS
    }
}