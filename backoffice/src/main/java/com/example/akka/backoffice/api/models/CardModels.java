package com.example.akka.backoffice.api.models;

public class CardModels {

    public record Card(String pan, String expiryDate, String cvv, String accountId) {}

    public record ValidateCardRequest(String pan, String expiryDate, String cvv) {}

    public record ValidateCardResponse(boolean isValid, String message) {}
}