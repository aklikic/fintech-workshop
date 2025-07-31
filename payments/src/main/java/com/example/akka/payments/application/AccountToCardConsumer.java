package com.example.akka.payments.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import com.example.akka.payments.domain.CardEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("account-to-card-consumer")
@Consume.FromEventSourcedEntity(value = CardEntity.class)
public class AccountToCardConsumer extends Consumer {
  
  private static final Logger logger = LoggerFactory.getLogger(AccountToCardConsumer.class);
  
  private ComponentClient componentClient;
  
  public AccountToCardConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }
  
  public Consumer.Effect onEvent(CardEvent event) {
    logger.info("Received a card event {}", event.getClass().getSimpleName());
    return switch (event) {
      case CardEvent.Created e -> onCardEventCreated(e);
      case CardEvent.Activated e -> onCardEventActivated(e);
    };
  }
  
  private Effect onCardEventActivated(CardEvent.Activated e) {
    componentClient.forKeyValueEntity(e.accountId())
        .method(AccountToCardEntity::activateCard)
        .invoke();
    return effects().done();
  }
  
  private Effect onCardEventCreated(CardEvent.Created e) {
    
    AccountToCardEntity.CreateCardCommand cmd = new AccountToCardEntity.CreateCardCommand(e.pan(), e.accountId());
    componentClient.forKeyValueEntity(e.accountId())
        .method(AccountToCardEntity::createCard)
        .invoke(cmd);
    
    return effects().done();
  }
}