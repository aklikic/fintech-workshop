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
        return effects().reply(AuthorisationResponse.ok(authOpt.map(AccountState.Authorisation::authCode).get()));
      }
      
      if (!currentState().isAvailableBalance(request.amount())) {
        return effects().reply(AuthorisationResponse.error(AuthorisationResult.declined, AuthorisationStatus.insufficient_funds));
      }
      
      var authCode = UUID.randomUUID().toString();
      var event = new AccountEvent.TransAuthorisationAdded(request.transactionId(), request.amount(), authCode);
      return effects().persist(event).thenReply(state -> AuthorisationResponse.ok(authCode));
      
    }
  }
  
  public Effect<Done> captureTransaction(String transactionId) {
    if (currentState().isEmpty()) {
      return effects().error("Account not found");
    }
    
    if (!currentState().getAuthorisation(transactionId).isPresent()) {
      //deduplication
      return effects().reply(Done.getInstance());
    }
    var event = new AccountEvent.TransCaptureAdded(transactionId);
    return effects().persist(event).thenReply(s -> Done.getInstance());
    
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
  
  
}