package com.example.akka.payments.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import com.example.akka.corebanking.api.PublicAccountEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("account-consumer")
@Consume.FromServiceStream(id="account-stream", service = "corebanking", ignoreUnknown = true)
public class AccountConsumer extends Consumer {

    private Logger logger = LoggerFactory.getLogger(AccountConsumer.class);
    public Effect onPublicEvent(PublicAccountEvent.Created event) {
        logger.info("Received a public account event: {}", event);
        return effects().done();
    }
}
