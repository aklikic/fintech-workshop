package com.example.akka.payments.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import com.example.akka.corebanking.api.PublicAccountEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("account-events-consumer")
@Consume.FromServiceStream(service = "corebanking", id = "corebanking_public_events")
public class AccountEventsConsumer extends Consumer {
  private static final Logger logger = LoggerFactory.getLogger(AccountEventsConsumer.class);
  
  private ComponentClient componentClient;
  
  public AccountEventsConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }
  
  public Effect onEvent(PublicAccountEvent event) {
    
    return switch (event) {
      case PublicAccountEvent.Created e -> {
        logger.info("Received a public account event {} for account id {}", event.getClass().getSimpleName(), e.accountId());
        
        var accountToCard = componentClient.forKeyValueEntity(e.accountId())
                .method(AccountToCardEntity::getAccountToCard)
                    .invoke();
        
        componentClient.forEventSourcedEntity(accountToCard.pan())
            .method(CardEntity::activate)
            .invoke();
        
        yield effects().done();
      }
    };
  }
}
