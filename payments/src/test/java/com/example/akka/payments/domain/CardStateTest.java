package com.example.akka.payments.domain;

import com.example.akka.payments.domain.CardEvent;
import com.example.akka.payments.domain.CardState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CardStateTest {

    @Test
    public void testEmptyCardState() {
        CardState emptyState = CardState.empty();
        
        assertEquals("", emptyState.pan());
        assertEquals("", emptyState.expiryDate());
        assertEquals("", emptyState.cvv());
        assertEquals("", emptyState.accountId());
        assertTrue(emptyState.isEmpty());
    }

    @Test
    public void testCardStateCreation() {
        CardState state = new CardState("1234567890123456", "12/27", "123", "account123");
        
        assertEquals("1234567890123456", state.pan());
        assertEquals("12/27", state.expiryDate());
        assertEquals("123", state.cvv());
        assertEquals("account123", state.accountId());
        assertFalse(state.isEmpty());
    }

    @Test
    public void testIsEmptyReturnsTrueForEmptyPan() {
        CardState state = new CardState("", "12/27", "123", "account123");
        assertTrue(state.isEmpty());
    }

    @Test
    public void testIsEmptyReturnsFalseForNonEmptyPan() {
        CardState state = new CardState("1234567890123456", "", "", "");
        assertFalse(state.isEmpty());
    }

    @Test
    public void testOnCreate() {
        CardState emptyState = CardState.empty();
        CardEvent.Created event = new CardEvent.Created("1234567890123456", "12/27", "123", "account123");
        
        CardState newState = emptyState.onCreate(event);
        
        assertEquals("1234567890123456", newState.pan());
        assertEquals("12/27", newState.expiryDate());
        assertEquals("123", newState.cvv());
        assertEquals("account123", newState.accountId());
        assertFalse(newState.isEmpty());
    }

    @Test
    public void testOnCreateFromExistingState() {
        CardState existingState = new CardState("old_pan", "old_date", "old_cvv", "old_account");
        CardEvent.Created event = new CardEvent.Created("new_pan", "new_date", "new_cvv", "new_account");
        
        CardState newState = existingState.onCreate(event);
        
        assertEquals("new_pan", newState.pan());
        assertEquals("new_date", newState.expiryDate());
        assertEquals("new_cvv", newState.cvv());
        assertEquals("new_account", newState.accountId());
    }

    @Test
    public void testCardStateImmutability() {
        CardState originalState = new CardState("1234567890123456", "12/27", "123", "account123");
        CardEvent.Created event = new CardEvent.Created("9876543210987654", "01/28", "456", "account456");
        
        CardState newState = originalState.onCreate(event);
        
        assertNotSame(originalState, newState);
        assertEquals("1234567890123456", originalState.pan());
        assertEquals("9876543210987654", newState.pan());
    }
}