package com.example.akka.payments.domain;

public record AccountToCard(String accountId, String pan, boolean active) {
  public AccountToCard activate() {
    return new AccountToCard(accountId, pan, true);
  }
}
