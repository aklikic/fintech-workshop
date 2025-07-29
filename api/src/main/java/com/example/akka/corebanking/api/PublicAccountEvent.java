package com.example.akka.corebanking.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

//@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
//@JsonSubTypes({
//        @JsonSubTypes.Type(value = PublicAccountEvent.Created.class, name = "created"),
//        @JsonSubTypes.Type(value = PublicAccountEvent.TransAuthorisationAdded.class, name = "auth-added"),
//        @JsonSubTypes.Type(value = PublicAccountEvent.TransCaptureAdded.class, name = "auth-captured")
//})
public interface PublicAccountEvent {
//    String getType();
    record Created(String accountId, int initialBalance) implements PublicAccountEvent {
//        @Override
//        public String getType() {
//            return "created";
//        }
    }

    record TransAuthorisationAdded(String accountId, String transactionId, int amount, String authCode) implements PublicAccountEvent {
//        @Override
//        public String getType() {
//            return "auth-added";
//        }
    }

    record TransCaptureAdded(String accountId, String transactionId) implements PublicAccountEvent {
//        @Override
//        public String getType() {
//            return "auth-captured";
//        }
    }
}
