package com.example.akka.corebanking.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import com.example.akka.corebanking.domain.AccountTransaction;

import java.util.Optional;

@ComponentId(("transaction-history"))
public class AccountTransactionEntity extends KeyValueEntity<AccountTransaction> {
  
  private final String entityId;
  
  public AccountTransactionEntity(KeyValueEntityContext context) {
    this.entityId = context.entityId();
  }
  
  @Override
  public AccountTransaction emptyState() {
    return AccountTransaction.empty(entityId);
  }
  
  public Effect<AccountTransaction> onAuth(String authCode) {
    AccountTransaction transactionHistory = currentState().onAuth(authCode);
    return effects()
        .updateState(transactionHistory)
        .thenReply(transactionHistory);
  }
  
  public Effect<AccountTransaction> onCapture() {
    AccountTransaction transactionHistory = currentState().onCapture();
    return effects()
        .updateState(transactionHistory)
        .thenReply(transactionHistory);
  }
  
  public ReadOnlyEffect<Optional<AccountTransaction>> getTransaction() {
    
    return currentState().isEmpty()
        ? effects().reply(Optional.empty())
        : effects().reply(Optional.of(currentState()));
  }
}
