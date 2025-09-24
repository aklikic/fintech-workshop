package com.example.akka.payments.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import com.example.akka.payments.domain.CardEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("account-to-card-mapping-consumer")
@Consume.FromEventSourcedEntity(value = CardEntity.class)
public class CardByAccountConsumer extends Consumer {
  
  private static final Logger logger = LoggerFactory.getLogger(CardByAccountConsumer.class);
  
  private ComponentClient componentClient;
  
  public CardByAccountConsumer(ComponentClient componentClient) {
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
        .method(CardByAccountEntity::activateCard)
        .invoke();
    return effects().done();
  }
  
  private Effect onCardEventCreated(CardEvent.Created e) {
    
    CardByAccountEntity.CreateCardCommand cmd = new CardByAccountEntity.CreateCardCommand(e.pan(), e.accountId());
    componentClient.forKeyValueEntity(e.accountId())
        .method(CardByAccountEntity::createCard)
        .invoke(cmd);
    
    return effects().done();
  }
}