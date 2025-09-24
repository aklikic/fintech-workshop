package com.example.akka.payments.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import com.example.akka.corebanking.api.PublicAccountEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("corebanking-service-event-consumer")
@Consume.FromServiceStream(service = "corebanking", id = "account_public_events")
public class CorebankingServiceEventConsumer extends Consumer {
  private static final Logger logger = LoggerFactory.getLogger(CorebankingServiceEventConsumer.class);
  
  private ComponentClient componentClient;
  
  public CorebankingServiceEventConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }
  
  public Effect onEvent(PublicAccountEvent.Created event) {
      logger.info("Received a public account event {} for account id {}", event.getClass().getSimpleName(), event.accountId());
      var accountToCard = componentClient.forKeyValueEntity(event.accountId())
              .method(CardByAccountEntity::getAccountToCard)
              .invoke();
      if(accountToCard.isPresent()) {
          componentClient.forEventSourcedEntity(accountToCard.get().pan())
                  .method(CardEntity::activate)
                  .invoke();
          return effects().done();
      } else {
          throw new RuntimeException("Unable to find account to card for account id " + event.accountId());
      }
  }
}
