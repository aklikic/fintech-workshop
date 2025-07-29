package com.example.akka.corebanking.application;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Produce;
import akka.javasdk.consumer.Consumer;
import com.example.akka.corebanking.api.PublicAccountEvent;
import com.example.akka.corebanking.domain.AccountEvent;

@ComponentId("account-stream-producer")
@Consume.FromEventSourcedEntity(AccountEntity.class)
@Produce.ServiceStream( id = "account-stream")
@Acl(allow = @Acl.Matcher(service = "*"))
public class AccountStreamProducer extends Consumer {

    public Effect onEvent(AccountEvent accountEvent) {
        return switch (accountEvent){
            case AccountEvent.Created evt ->
                    effects().produce(new PublicAccountEvent.Created(evt.accountId(), evt.initialBalance()));
            case AccountEvent.TransAuthorisationAdded evt -> effects().produce(new PublicAccountEvent.TransAuthorisationAdded(messageContext().eventSubject().get(), evt.transactionId(), evt.amount(), evt.authCode()));
            case AccountEvent.TransCaptureAdded evt -> effects().produce(new PublicAccountEvent.TransCaptureAdded(messageContext().eventSubject().get(),evt.transactionId()));
        };
    }
}
