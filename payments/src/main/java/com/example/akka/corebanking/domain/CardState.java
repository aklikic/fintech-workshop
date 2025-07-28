package com.example.akka.corebanking.domain;

public record CardState(String pan, String expiryDate, String cvv, String accountId) {
    public static CardState empty() {
        return new CardState("", "", "", "");
    }

    public boolean isEmpty() {
        return pan.isEmpty();
    }

    public CardState onCreate(CardEvent.Created event) {
        return new CardState(event.pan(), event.expiryDate(), event.cvv(), event.accountId());
    }
}
