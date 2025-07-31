package com.example.akka.corebanking.api;

public sealed interface PublicAccountEvent {
  
  record Created(String accountId, int initialBalance) implements PublicAccountEvent {
  }
}
