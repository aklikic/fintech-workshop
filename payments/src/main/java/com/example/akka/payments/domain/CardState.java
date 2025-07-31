package com.example.akka.payments.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record CardState(String pan, String expiryDate, String cvv, String accountId, boolean active) {
    
    private static final Logger logger = LoggerFactory.getLogger(CardState.class);
    public static CardState empty() {
        return new CardState("", "", "", "", false);
    }

    public boolean isEmpty() {
        return pan.isEmpty();
    }

    public CardState onCreate(CardEvent.Created event) {
        return new CardState(event.pan(), event.expiryDate(), event.cvv(), event.accountId(), false);
    }

    public CardState onActivated() {
        
        logger.info("Card {} has been activated", pan());
        return new CardState(pan(), expiryDate(), cvv(), accountId(), true);
    }
}
