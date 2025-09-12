package com.example.akka.corebanking.application;


import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import com.example.akka.corebanking.domain.AccountEvent;
import com.example.akka.corebanking.domain.TransactionHistory;

@ComponentId("transaction-history-builder")
@Consume.FromEventSourcedEntity(AccountEntity.class)
public class TransactionHistoryBuilder extends Consumer {
  
  private final ComponentClient componentClient;
  
  public TransactionHistoryBuilder(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }
  
  public Effect consume(AccountEvent event) {
    
    var accountId = messageContext().eventSubject().get();
    
    return switch (event) {
       case AccountEvent.TransAuthorisationAdded auth -> {
        TransactionHistory.TransactionHistoryId id = new TransactionHistory.TransactionHistoryId(auth.transactionId(), accountId);
        componentClient.forKeyValueEntity(id.toString())
            .method(TransactionHistoryEntity::onAuth)
            .invoke(auth.authCode());
        yield effects().done();
      }
      
      case AccountEvent.TransCaptureAdded auth -> {
        TransactionHistory.TransactionHistoryId id = new TransactionHistory.TransactionHistoryId(auth.transactionId(), accountId);
        componentClient.forKeyValueEntity(id.toString())
            .method(TransactionHistoryEntity::onCapture)
            .invoke();
        yield effects().done();
      }
      default -> effects().done();
    };
    
  }
}
