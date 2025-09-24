package com.example.akka.corebanking.domain;

import org.slf4j.Logger;

public record AccountTransaction(AccountTransactionId id, String status, String authCode) {
  
  private static final Logger logger = org.slf4j.LoggerFactory.getLogger(AccountTransaction.class);
  
  public boolean isAuthorized() {
      return "AUTH".equals(status);
  }
  
  public boolean isCaptured() {
    return "CAPTURED".equals(status);
  }
  
  public record AccountTransactionId(String transactionId, String accountId) {
    public String toString() {
      return String.format("%s_%s", transactionId, accountId);
    }
    public static AccountTransactionId fromString(String s) {
      var parts = s.split("_");
      return new AccountTransactionId(parts[0], parts[1]);
    }
  }
  
  public static AccountTransaction empty(String transactionId) {
    return new AccountTransaction(AccountTransactionId.fromString(transactionId), "", "");
  }
  
  public boolean isEmpty() {
    return status.isEmpty();
  }
  
  public AccountTransaction onAuth(String authCode) {
    logger.info("Adding auth to history trxID {} for account {}", id.transactionId(), id.accountId());
    return new AccountTransaction(id, "AUTH", authCode);
  }
  
  public AccountTransaction onCapture() {
    logger.info("Adding capture to history trxID {} for account {}", id.transactionId(), id.accountId());
    return new AccountTransaction(id, "CAPTURED", authCode);
  }
}
