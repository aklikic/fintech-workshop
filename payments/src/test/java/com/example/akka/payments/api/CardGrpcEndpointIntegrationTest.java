package com.example.akka.payments.api;

import akka.javasdk.DependencyProvider;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import com.example.akka.account.api.AccountGrpcEndpointClient;
import com.example.akka.payments.api.Card;
import com.example.akka.payments.api.CardGrpcEndpointClient;
import com.example.akka.payments.api.GetCardRequest;
import com.example.akka.payments.api.ValidateCardRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CardGrpcEndpointIntegrationTest extends TestKitSupport {

    @Override
    protected TestKit.Settings testKitSettings() {
        // Create custom DependencyProvider that provides mocked AccountGrpcEndpointClient
        DependencyProvider mockDependencyProvider = new DependencyProvider() {
            @SuppressWarnings("unchecked")
            @Override
            public <T> T getDependency(Class<T> clazz) {
               return null;
            }
        };

        return TestKit.Settings.DEFAULT.withDependencyProvider(mockDependencyProvider);
    }

    @Test
    public void testCreateCard() {
        var client = getGrpcEndpointClient(CardGrpcEndpointClient.class);
        
        var cardRequest = Card.newBuilder()
                .setPan("1234567890123456")
                .setExpiryDate("12/27")
                .setCvv("123")
                .setAccountId("account123")
                .build();
        
        var response = client.createCard().invoke(cardRequest);
        
        assertEquals("1234567890123456", response.getPan());
        assertEquals("12/27", response.getExpiryDate());
        assertEquals("123", response.getCvv());
        assertEquals("account123", response.getAccountId());
    }

    @Test
    public void testCreateCardTwiceReturnsSameCard() {
        var client = getGrpcEndpointClient(CardGrpcEndpointClient.class);
        
        var cardRequest = Card.newBuilder()
                .setPan("1111222233334444")
                .setExpiryDate("01/25")
                .setCvv("456")
                .setAccountId("existing_account")
                .build();
        
        var firstResponse = client.createCard().invoke(cardRequest);
        
        var secondRequest = Card.newBuilder()
                .setPan("1111222233334444")
                .setExpiryDate("06/28")
                .setCvv("789")
                .setAccountId("new_account")
                .build();
        
        var secondResponse = client.createCard().invoke(secondRequest);
        
        assertEquals(firstResponse.getPan(), secondResponse.getPan());
        assertEquals(firstResponse.getExpiryDate(), secondResponse.getExpiryDate());
        assertEquals(firstResponse.getCvv(), secondResponse.getCvv());
        assertEquals(firstResponse.getAccountId(), secondResponse.getAccountId());
    }

    @Test
    public void testGetCard() {
        var client = getGrpcEndpointClient(CardGrpcEndpointClient.class);
        
        var createRequest = Card.newBuilder()
                .setPan("9999888877776666")
                .setExpiryDate("03/26")
                .setCvv("321")
                .setAccountId("test_account")
                .build();
        
        client.createCard().invoke(createRequest);
        
        var getRequest = GetCardRequest.newBuilder()
                .setPan("9999888877776666")
                .build();
        
        var response = client.getCard().invoke(getRequest);
        
        assertEquals("9999888877776666", response.getPan());
        assertEquals("03/26", response.getExpiryDate());
        assertEquals("321", response.getCvv());
        assertEquals("test_account", response.getAccountId());
    }

    @Test
    public void testGetNonExistentCard() {
        var client = getGrpcEndpointClient(CardGrpcEndpointClient.class);
        
        var getRequest = GetCardRequest.newBuilder()
                .setPan("0000000000000000")
                .build();
        
        assertThrows(Exception.class, () -> {
            client.getCard().invoke(getRequest);
        });
    }

    @Test
    public void testValidateCardWithCorrectDetails() {
        var client = getGrpcEndpointClient(CardGrpcEndpointClient.class);
        
        var createRequest = Card.newBuilder()
                .setPan("5555444433332222")
                .setExpiryDate("08/25")
                .setCvv("987")
                .setAccountId("validate_account")
                .build();
        
        client.createCard().invoke(createRequest);
        
        var validateRequest = ValidateCardRequest.newBuilder()
                .setPan("5555444433332222")
                .setExpiryDate("08/25")
                .setCvv("987")
                .build();
        
        var response = client.validateCard().invoke(validateRequest);
        
        assertTrue(response.getIsValid());
    }

    @Test
    public void testValidateCardWithIncorrectCvv() {
        var client = getGrpcEndpointClient(CardGrpcEndpointClient.class);
        
        var createRequest = Card.newBuilder()
                .setPan("4444333322221111")
                .setExpiryDate("09/26")
                .setCvv("654")
                .setAccountId("validate_account2")
                .build();
        
        client.createCard().invoke(createRequest);
        
        var validateRequest = ValidateCardRequest.newBuilder()
                .setPan("4444333322221111")
                .setExpiryDate("09/26")
                .setCvv("wrong_cvv")
                .build();
        
        var response = client.validateCard().invoke(validateRequest);
        
        assertFalse(response.getIsValid());
    }

    @Test
    public void testValidateCardWithExpiredDate() {
        var client = getGrpcEndpointClient(CardGrpcEndpointClient.class);
        
        var createRequest = Card.newBuilder()
                .setPan("7777666655554444")
                .setExpiryDate("12/25")
                .setCvv("111")
                .setAccountId("expired_account")
                .build();
        
        client.createCard().invoke(createRequest);
        
        var validateRequest = ValidateCardRequest.newBuilder()
                .setPan("7777666655554444")
                .setExpiryDate("01/24")
                .setCvv("111")
                .build();
        
        var response = client.validateCard().invoke(validateRequest);
        
        assertFalse(response.getIsValid());
    }

    @Test
    public void testValidateNonExistentCard() {
        var client = getGrpcEndpointClient(CardGrpcEndpointClient.class);
        
        var validateRequest = ValidateCardRequest.newBuilder()
                .setPan("0000111122223333")
                .setExpiryDate("12/27")
                .setCvv("999")
                .build();
        
        assertThrows(Exception.class, () -> {
            client.validateCard().invoke(validateRequest);
        });
    }
}