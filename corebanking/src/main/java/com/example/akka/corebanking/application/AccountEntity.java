package com.example.akka.corebanking.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import com.example.akka.corebanking.domain.AccountEvent;
import com.example.akka.corebanking.domain.AccountState;

import java.util.Optional;
import java.util.UUID;

@ComponentId("account-entity")
public class AccountEntity extends EventSourcedEntity<AccountState, AccountEvent> {
  
  @Override
  public AccountState emptyState() {
    return AccountState.empty();
  }
  
  public Effect<ApiAccount> createAccount(ApiAccount in) {
    if (currentState().isEmpty()) {
      var event = new AccountEvent.Created(in.accountId(), in.availableBalance());
      return effects().persist(event).thenReply(this::fromState);
    } else {
      return effects().reply(fromState(currentState()));
    }
  }
  
  public ReadOnlyEffect<ApiAccount> getAccount() {
    if (currentState().isEmpty()) {
      return effects().error("Account not found");
    } else {
      return effects().reply(fromState(currentState()));
    }
  }
  
  public Effect<AuthorisationResponse> authoriseTransaction(AuthorisationRequest request) {
    if (currentState().isEmpty()) {
      return effects().reply(AuthorisationResponse.error(AuthorisationResult.declined, AuthorisationStatus.account_not_found));
    } else {
      var authOpt = currentState().getAuthorisation(request.transactionId());
      if (authOpt.isPresent()) {
        //deduplication
        return effects()
                .reply(AuthorisationResponse.ok(authOpt.map(AccountState.Authorisation::authCode).get()));
      }
      
      if (!currentState().isAvailableBalance(request.amount())) {
        return effects()
                .reply(AuthorisationResponse.error(AuthorisationResult.declined, AuthorisationStatus.insufficient_funds));
      }
      
      var authCode = UUID.randomUUID().toString();
      var event = new AccountEvent.TransAuthorisationAdded(request.transactionId(), request.amount(), authCode);
      return effects()
              .persist(event)
              .thenReply(state -> AuthorisationResponse.ok(authCode));
      
    }
  }
  
  public Effect<CaptureTransactionResponse> captureTransaction(String transactionId) {
    if (currentState().isEmpty()) {
      return effects()
              .reply(CaptureTransactionResponse.error(CaptureTransactionResult.declined, CaptureTransactionStatus.account_not_found));
    }
    var maybeTrans = currentState().getAuthorisation(transactionId);
    if (!maybeTrans.isPresent()) {
      //deduplication
      return effects()
              .reply(CaptureTransactionResponse.error(CaptureTransactionResult.declined, CaptureTransactionStatus.transaction_not_found));
    }
    var event = new AccountEvent.TransCaptureAdded(transactionId, maybeTrans.get().amount());
    return effects()
            .persist(event)
            .thenReply(s -> CaptureTransactionResponse.ok());
    
  }

    public Effect<CancelTransactionResponse> cancelTransaction(String transactionId) {
        if (currentState().isEmpty()) {
            return effects()
                    .reply(CancelTransactionResponse.error(CancelTransactionResult.declined, CancelTransactionStatus.account_not_found));
        }
        var maybeTrans = currentState().getAuthorisation(transactionId);
        if (!maybeTrans.isPresent()) {
            //deduplication
            return effects()
                    .reply(CancelTransactionResponse.error(CancelTransactionResult.declined, CancelTransactionStatus.transaction_not_found));
        }
        var event = new AccountEvent.TransCancelAdded(transactionId, maybeTrans.get().amount());
        return effects()
                .persist(event)
                .thenReply(s -> CancelTransactionResponse.ok());

    }
  
  
  private ApiAccount fromState(AccountState state) {
    return new ApiAccount(
        state.accountId(),
        state.availableBalance(),
        state.postedBalance()
    );
  }
  
  @Override
  public AccountState applyEvent(AccountEvent accountEvent) {
    return switch (accountEvent) {
      case AccountEvent.Created created -> currentState().onCreate(created);
      case AccountEvent.TransAuthorisationAdded auth -> currentState().onAuthorisationAdded(auth);
      case AccountEvent.TransCaptureAdded capture -> currentState().onCaptureAdded(capture);
      case AccountEvent.TransCancelAdded cancel -> currentState().onCancelAdded(cancel);
    };
  }
  
  public record ApiAccount(String accountId, int availableBalance, int postedBalance) {
    
    public static ApiAccount empty() {
      return new ApiAccount("", 0, 0);
    }
    
    public boolean isEmpty() {
      return accountId == null || accountId.isEmpty();
    }
  }
  
  public record AuthorisationRequest(String transactionId, int amount) {
  }
  
  public record AuthorisationResponse(Optional<String> authCode, AuthorisationResult authResult,
                                      AuthorisationStatus authStatus) {
    public static AuthorisationResponse ok(String authCode) {
      return new AuthorisationResponse(Optional.of(authCode), AuthorisationResult.authorised, AuthorisationStatus.ok);
    }
    
    public static AuthorisationResponse error(AuthorisationResult authResult, AuthorisationStatus authStatus) {
      return new AuthorisationResponse(Optional.empty(), authResult, authStatus);
    }
  }
  
  public enum AuthorisationResult {
    authorised, declined
  }
  
  public enum AuthorisationStatus {
    ok, card_not_found, insufficient_funds, account_closed, undiscosed, account_not_found
  }

    public record CaptureTransactionResponse(CaptureTransactionResult captureResult,
                                             CaptureTransactionStatus captureStatus) {
        public static CaptureTransactionResponse ok() {
            return new CaptureTransactionResponse(CaptureTransactionResult.captured, CaptureTransactionStatus.ok);
        }

        public static CaptureTransactionResponse error(CaptureTransactionResult captureResult, CaptureTransactionStatus captureStatus) {
            return new CaptureTransactionResponse(captureResult, captureStatus);
        }
    }

    public enum CaptureTransactionResult {
        captured, declined
    }

    public enum CaptureTransactionStatus {
        ok, undiscosed, account_not_found, transaction_not_found
    }

    public record CancelTransactionResponse(CancelTransactionResult cancelResult,
                                            CancelTransactionStatus cancelStatus) {
        public static CancelTransactionResponse ok() {
            return new CancelTransactionResponse(CancelTransactionResult.canceled, CancelTransactionStatus.ok);
        }

        public static CancelTransactionResponse error(CancelTransactionResult cancelResult, CancelTransactionStatus cancelStatus) {
            return new CancelTransactionResponse(cancelResult, cancelStatus);
        }
    }

    public enum CancelTransactionResult {
        canceled, declined
    }

    public enum CancelTransactionStatus {
        ok, undiscosed, account_not_found, transaction_not_found
    }
  
  
}