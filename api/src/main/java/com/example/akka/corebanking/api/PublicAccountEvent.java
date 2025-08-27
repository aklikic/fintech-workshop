package com.example.akka.corebanking.api;

public sealed interface PublicAccountEvent {
    record Created(String accountId, int initialBalance) implements PublicAccountEvent {}

    record TransAuthorisationAdded(String accountId, String transactionId, int amount, String authCode) implements PublicAccountEvent {}

    record TransCaptureAdded(String accountId, String transactionId) implements PublicAccountEvent {}
}