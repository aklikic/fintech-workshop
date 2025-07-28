package com.example.akka.corebanking.domain;

import org.slf4j.Logger;

public record TransactionHistory(TransactionHistoryId id, String status, String authCode) {
  
  private static final Logger logger = org.slf4j.LoggerFactory.getLogger(TransactionHistory.class);
  
  public boolean isAuthorized() {
      return "AUTH".equals(status);
  }
  
  public boolean isCaptured() {
    return "CAPTURED".equals(status);
  }
  
  public record TransactionHistoryId(String transactionId, String accountId) {
    public String toString() {
      return String.format("%s_%s", transactionId, accountId);
    }
    public static TransactionHistoryId fromString(String s) {
      var parts = s.split("_");
      return new TransactionHistoryId(parts[0], parts[1]);
    }
  }
  
  public static TransactionHistory empty(String transactionId) {
    return new TransactionHistory(TransactionHistoryId.fromString(transactionId), "", "");
  }
  
  public boolean isEmpty() {
    return status.isEmpty();
  }
  
  public  TransactionHistory onAuth(String authCode) {
    logger.info("Adding auth to history trxID {} for account {}", id.transactionId(), id.accountId());
    return new TransactionHistory(id, "AUTH", authCode);
  }
  
  public TransactionHistory onCapture() {
    logger.info("Adding capture to history trxID {} for account {}", id.transactionId(), id.accountId());
    return new TransactionHistory(id, "CAPTURED", authCode);
  }
}
