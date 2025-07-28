package com.example.akka.corebanking.api;

import akka.grpc.GrpcServiceException;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.GrpcEndpoint;
import akka.javasdk.client.ComponentClient;
import com.example.akka.account.api.*;
import com.example.akka.corebanking.application.AccountEntity;
import com.example.akka.corebanking.application.TransactionHistoryEntity;
import com.example.akka.corebanking.domain.TransactionHistory;
import io.grpc.Status;
import org.slf4j.Logger;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@GrpcEndpoint
public class AccountGrpcEndpointImpl implements AccountGrpcEndpoint {

    private final static Logger logger = org.slf4j.LoggerFactory.getLogger(AccountGrpcEndpointImpl.class);
    private final ComponentClient componentClient;

    public AccountGrpcEndpointImpl(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

/* <<<<<<<<<<<<<<  ✨ Windsurf Command ⭐ >>>>>>>>>>>>>>>> */
    /**
     * Creates a new account with the given account ID and initial balance.
     *
     * @param in the request containing the account ID and initial balance
     * @return the created account
     * @throws GrpcServiceException if an error occurs during account creation
     */

/* <<<<<<<<<<  9bf84039-ec58-4e9b-b369-411c670c349a  >>>>>>>>>>> */
    @Override
    public Account createAccount(CreateAccountRequest in) {
        logger.info("Creating account {}", in.getAccountId());
        try {
            var res = componentClient.forEventSourcedEntity(in.getAccountId())
                    .method(AccountEntity::createAccount)
                    .invoke(new AccountEntity.ApiAccount(in.getAccountId(), in.getInitialBalance(), in.getInitialBalance()));
            return fromState(res);
        } catch (Exception e) {
            throw new GrpcServiceException(Status.INTERNAL.augmentDescription(e.getMessage()));
        }
    }

    @Override
    public Account getAccount(GetAccountRequest in) {
        logger.info("Getting account {}", in.getAccountId());
        try {
            var account = componentClient.forEventSourcedEntity(in.getAccountId())
                    .method(AccountEntity::getAccount)
                    .invoke();
            if (account.isEmpty()) {
                throw new GrpcServiceException(Status.NOT_FOUND);
            }
            return fromState(account);
        } catch (Exception e) {
            throw new GrpcServiceException(Status.INTERNAL.augmentDescription(e.getMessage()));
        }
    }

    @Override
    public AuthorizeTransactionResponse authorizeTransaction(AuthorizeTransactionRequest in) {
        logger.info("Authorizing transaction {} for account {} amount {}", 
                   in.getTransactionId(), in.getAccountId(), in.getAmount());
        
        TransactionHistory.TransactionHistoryId trxId = new TransactionHistory.TransactionHistoryId(in.getTransactionId(), in.getAccountId());
        var transactionHistory = componentClient.forKeyValueEntity(trxId.toString())
            .method(TransactionHistoryEntity::getTransaction)
            .invoke();
        
        if (transactionHistory.isPresent() && (transactionHistory.get().isAuthorized() || transactionHistory.get().isCaptured())) {
            
            logger.info("Duplicate transaction {}. Returning from cache", in.getTransactionId());
            return AuthorizeTransactionResponse.newBuilder()
                .setAuthCode(transactionHistory.get().authCode())
                .setAuthResult(AuthResult.AUTHORISED)
                .setAuthStatus(AuthStatus.OK)
                .build();
        }
        
        try {
            
            var authRequest = new AccountEntity.AuthorisationRequest(in.getTransactionId(), in.getAmount());
            var response = componentClient.forEventSourcedEntity(in.getAccountId())
                    .method(AccountEntity::authoriseTransaction)
                    .invoke(authRequest);
            
            return AuthorizeTransactionResponse.newBuilder()
                    .setAuthCode(response.authCode().orElse(""))
                    .setAuthResult(toProtoAuthResult(response.authResult()))
                    .setAuthStatus(toProtoAuthStatus(response.authStatus()))
                    .build();
        } catch (Exception e) {
            throw new GrpcServiceException(Status.INTERNAL.augmentDescription(e.getMessage()));
        }
    }

    @Override
    public CaptureTransactionResponse captureTransaction(CaptureTransactionRequest in) {
        logger.info("Capturing transaction {} for account {}", in.getTransactionId(), in.getAccountId());
        
        TransactionHistory.TransactionHistoryId trxId = new TransactionHistory.TransactionHistoryId(in.getTransactionId(), in.getAccountId());
        var transactionHistory = componentClient.forKeyValueEntity(trxId.toString())
            .method(TransactionHistoryEntity::getTransaction)
            .invoke();
        
        if (transactionHistory.isPresent() && transactionHistory.get().isCaptured()){
            logger.info("Duplicate transaction {}. Returning from cache", in.getTransactionId());
            return CaptureTransactionResponse.newBuilder().setSuccess(true).build();
        }
        
        try {
            componentClient.forEventSourcedEntity(in.getAccountId())
                    .method(AccountEntity::captureTransaction)
                    .invoke(in.getTransactionId());
            
            return CaptureTransactionResponse.newBuilder()
                    .setSuccess(true)
                    .build();
        } catch (Exception e) {
            logger.error("Failed to capture transaction {} for account {}: {}", 
                        in.getTransactionId(), in.getAccountId(), e.getMessage());
            throw new GrpcServiceException(Status.INTERNAL.augmentDescription(e.getMessage()));
        }
    }

    private Account fromState(AccountEntity.ApiAccount account) {
        return Account.newBuilder()
                .setAccountId(account.accountId())
                .setAvailableBalance(account.availableBalance())
                .setPostedBalance(account.postedBalance())
                .build();
    }
    
    private AuthResult toProtoAuthResult(AccountEntity.AuthorisationResult result) {
        return switch (result) {
            case authorised -> AuthResult.AUTHORISED;
            case declined -> AuthResult.DECLINED;
        };
    }
    
    private AuthStatus toProtoAuthStatus(AccountEntity.AuthorisationStatus status) {
        return switch (status) {
            case ok -> AuthStatus.OK;
            case card_not_found -> AuthStatus.CARD_NOT_FOUND;
            case insufficient_funds -> AuthStatus.INSUFFICIENT_FUNDS;
            case account_closed -> AuthStatus.ACCOUNT_CLOSED;
            case undiscosed -> AuthStatus.UNDISCLOSED;
            case account_not_found -> AuthStatus.ACCOUNT_NOT_FOUND;
        };
    }
}
