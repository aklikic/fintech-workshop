package com.example.akka.corebanking.domain;

public sealed interface AccountEvent {
  record Created(String accountId, int initialBalance) implements AccountEvent { }
  
  record TransAuthorisationAdded(String transactionId, int amount, String authCode) implements AccountEvent { }
  
  record TransCaptureAdded(String transactionId, int amount) implements AccountEvent {}

  record TransCancelAdded(String transactionId, int amount) implements AccountEvent {}
}