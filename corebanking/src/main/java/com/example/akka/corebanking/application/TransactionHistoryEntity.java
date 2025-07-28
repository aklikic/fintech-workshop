package com.example.akka.corebanking.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import com.example.akka.corebanking.domain.TransactionHistory;

import java.util.Optional;

@ComponentId(("transaction-history"))
public class TransactionHistoryEntity extends KeyValueEntity<TransactionHistory> {
  
  private final String entityId;
  
  public TransactionHistoryEntity(KeyValueEntityContext context) {
    this.entityId = context.entityId();
  }
  
  @Override
  public TransactionHistory emptyState() {
    return TransactionHistory.empty(entityId);
  }
  
  public Effect<TransactionHistory> onAuth(String authCode) {
    TransactionHistory transactionHistory = currentState().onAuth(authCode);
    return effects()
        .updateState(transactionHistory)
        .thenReply(transactionHistory);
  }
  
  public Effect<TransactionHistory> onCapture() {
    TransactionHistory transactionHistory = currentState().onCapture();
    return effects()
        .updateState(transactionHistory)
        .thenReply(transactionHistory);
  }
  
  public ReadOnlyEffect<Optional<TransactionHistory>> getTransaction() {
    
    return currentState().isEmpty()
        ? effects().reply(Optional.empty())
        : effects().reply(Optional.of(currentState()));
  }
}
