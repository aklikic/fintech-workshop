package com.example.akka.payments.domain;

public sealed interface CardEvent {
    record Created(String pan, String expiryDate, String cvv, String accountId) implements CardEvent {
    }

    record Activated(String pan,String accountId) implements CardEvent { }
}