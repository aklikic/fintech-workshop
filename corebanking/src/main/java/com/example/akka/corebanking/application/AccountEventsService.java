package com.example.akka.corebanking.application;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Produce;
import akka.javasdk.consumer.Consumer;
import com.example.akka.corebanking.api.PublicAccountEvent;
import com.example.akka.corebanking.domain.AccountEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("account-events-service")
@Consume.FromEventSourcedEntity(AccountEntity.class)
@Produce.ServiceStream(id = "corebanking_public_events")
@Acl(allow = @Acl.Matcher(service = "*"))
public class AccountEventsService extends Consumer {
  
  private final Logger logger = LoggerFactory.getLogger(AccountEventsService.class);
  
  public Effect onEvent(AccountEvent evt) {
    return switch (evt) {
      case AccountEvent.Created e -> onCreated(e);
      case AccountEvent.TransAuthorisationAdded e -> done(e);
      case AccountEvent.TransCaptureAdded e -> done(e);
    };
  }
  
  private Effect onCreated(AccountEvent.Created evt) {
    logger.info("Producing a public account event {} for account id {}", evt.getClass().getSimpleName(), evt.accountId());
    return effects().produce(new PublicAccountEvent.Created(evt.accountId(), evt.initialBalance()));
  }
  
  private Effect done(AccountEvent evt) {
    logger.info("Ignoring an account event {}", evt.getClass().getSimpleName());
    return effects().done();
  }
}
