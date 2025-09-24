package com.example.akka.corebanking.application;


import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import com.example.akka.corebanking.domain.AccountEvent;
import com.example.akka.corebanking.domain.AccountTransaction;

@ComponentId("account-transaction-builder")
@Consume.FromEventSourcedEntity(AccountEntity.class)
public class AccountTransactionBuilder extends Consumer {
  
  private final ComponentClient componentClient;
  
  public AccountTransactionBuilder(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }
  
  public Effect consume(AccountEvent event) {
    
    var accountId = messageContext().eventSubject().get();
    
    return switch (event) {
       case AccountEvent.TransAuthorisationAdded auth -> {
        AccountTransaction.AccountTransactionId id = new AccountTransaction.AccountTransactionId(auth.transactionId(), accountId);
        componentClient.forKeyValueEntity(id.toString())
            .method(AccountTransactionEntity::onAuth)
            .invoke(auth.authCode());
        yield effects().done();
      }
      
      case AccountEvent.TransCaptureAdded auth -> {
        AccountTransaction.AccountTransactionId id = new AccountTransaction.AccountTransactionId(auth.transactionId(), accountId);
        componentClient.forKeyValueEntity(id.toString())
            .method(AccountTransactionEntity::onCapture)
            .invoke();
        yield effects().done();
      }
      default -> effects().done();
    };
    
  }
}
