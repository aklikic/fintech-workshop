package com.example.akka.payments.application;

import akka.javasdk.testkit.EventSourcedTestKit;
import com.example.akka.payments.domain.CardEvent;
import com.example.akka.payments.domain.CardState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CardEntityTest {

    @Test
    public void testCreateCardWhenEmpty() {
        var testKit = EventSourcedTestKit.of(CardEntity::new);
        
        var cardRequest = new CardEntity.ApiCard(
                "1234567890123456",
                "12/27",
                "123",
                "account123"
        );
        var result = testKit.method(CardEntity::createCard).invoke(cardRequest);
        
        assertEquals("1234567890123456", result.getReply().pan());
        assertEquals("12/27", result.getReply().expiryDate());
        assertEquals("123", result.getReply().cvv());
        assertEquals("account123", result.getReply().accountId());
        
        var createdEvent = result.getNextEventOfType(CardEvent.Created.class);
        assertEquals("1234567890123456", createdEvent.pan());
        assertEquals("12/27", createdEvent.expiryDate());
        assertEquals("123", createdEvent.cvv());
        assertEquals("account123", createdEvent.accountId());

        var state = (CardState)result.getUpdatedState();
        assertFalse(state.isEmpty());
        assertEquals("1234567890123456", state.pan());
        assertEquals("12/27", state.expiryDate());
        assertEquals("123", state.cvv());
        assertEquals("account123", state.accountId());
    }

    @Test
    public void testCreateCardWhenCardAlreadyExists() {
        var testKit = EventSourcedTestKit.of(CardEntity::new);
        
        var existingCard = new CardEntity.ApiCard(
                "1111222233334444",
                "01/25",
                "456",
                "existing_account"
        );
        
        testKit.method(CardEntity::createCard).invoke(existingCard);
        
        var newCardRequest = new CardEntity.ApiCard(
                "5555666677778888",
                "06/28",
                "789",
                "new_account"
        );
        
        var result = testKit.method(CardEntity::createCard).invoke(newCardRequest);
        
        assertEquals("1111222233334444", result.getReply().pan());
        assertEquals("01/25", result.getReply().expiryDate());
        assertEquals("456", result.getReply().cvv());
        assertEquals("existing_account", result.getReply().accountId());
        
        assertFalse(result.didPersistEvents());
    }

    @Test
    public void testGetCardWhenExists() {
        var testKit = EventSourcedTestKit.of(CardEntity::new);
        
        var cardRequest = new CardEntity.ApiCard(
                "9999888877776666",
                "03/26",
                "321",
                "test_account"
        );
        
        testKit.method(CardEntity::createCard).invoke(cardRequest);
        
        var result = testKit.method(CardEntity::getCard).invoke();
        
        assertEquals("9999888877776666", result.getReply().pan());
        assertEquals("03/26", result.getReply().expiryDate());
        assertEquals("321", result.getReply().cvv());
        assertEquals("test_account", result.getReply().accountId());
        
        assertFalse(result.didPersistEvents());
    }

    @Test
    public void testGetCardWhenNotExists() {
        var testKit = EventSourcedTestKit.of(CardEntity::new);
        
        var result = testKit.method(CardEntity::getCard).invoke();
        
        assertTrue(result.isError());
        assertEquals("Card not found", result.getError());
    }

    @Test
    public void testEmptyState() {
        var testKit = EventSourcedTestKit.of(CardEntity::new);
        
        var state = testKit.getState();
        assertTrue(state.isEmpty());
        assertEquals("", state.pan());
        assertEquals("", state.expiryDate());
        assertEquals("", state.cvv());
        assertEquals("", state.accountId());
    }
    
}