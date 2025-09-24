package com.example.akka.corebanking.domain;

import com.example.akka.corebanking.domain.AccountEvent;
import com.example.akka.corebanking.domain.AccountState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AccountStateTest {

    @Test
    public void testEmptyAccountState() {
        AccountState emptyState = AccountState.empty();
        
        assertEquals("", emptyState.accountId());
        assertEquals(List.of(), emptyState.authorisations());
        assertEquals(0, emptyState.availableBalance());
        assertEquals(0, emptyState.postedBalance());
        assertTrue(emptyState.isEmpty());
    }

    @Test
    public void testAccountStateCreation() {
        var authorisations = List.of(new AccountState.Authorisation("tx1", 100, "auth123"));
        AccountState state = new AccountState("account123", authorisations, 500, 400);
        
        assertEquals("account123", state.accountId());
        assertEquals(1, state.authorisations().size());
        assertEquals("tx1", state.authorisations().get(0).transactionId());
        assertEquals(100, state.authorisations().get(0).amount());
        assertEquals("auth123", state.authorisations().get(0).authCode());
        assertEquals(500, state.availableBalance());
        assertEquals(400, state.postedBalance());
        assertFalse(state.isEmpty());
    }

    @Test
    public void testIsEmptyReturnsTrueForEmptyAccountId() {
        AccountState state = new AccountState("", List.of(), 100, 50);
        assertTrue(state.isEmpty());
    }

    @Test
    public void testIsEmptyReturnsFalseForNonEmptyAccountId() {
        AccountState state = new AccountState("account123", List.of(), 0, 0);
        assertFalse(state.isEmpty());
    }

    @Test
    public void testOnCreate() {
        AccountState emptyState = AccountState.empty();
        AccountEvent.Created event = new AccountEvent.Created("account123", 1000);
        
        AccountState newState = emptyState.onCreate(event);
        
        assertEquals("account123", newState.accountId());
        assertEquals(List.of(), newState.authorisations());
        assertEquals(1000, newState.availableBalance());
        assertEquals(1000, newState.postedBalance());
        assertFalse(newState.isEmpty());
    }

    @Test
    public void testOnCreateFromExistingState() {
        var existingAuths = List.of(new AccountState.Authorisation("tx1", 100, "auth1"));
        AccountState existingState = new AccountState("old_account", existingAuths, 500, 400);
        AccountEvent.Created event = new AccountEvent.Created("new_account", 750);
        
        AccountState newState = existingState.onCreate(event);
        
        assertEquals("new_account", newState.accountId());
        assertEquals(List.of(), newState.authorisations());
        assertEquals(750, newState.availableBalance());
        assertEquals(750, newState.postedBalance());
    }

    @Test
    public void testOnAuthorisationAdded() {
        AccountState state = new AccountState("account123", List.of(), 1000, 500);
        AccountEvent.TransAuthorisationAdded event = new AccountEvent.TransAuthorisationAdded("tx1", 200, "auth123");
        
        AccountState newState = state.onAuthorisationAdded(event);
        
        assertEquals("account123", newState.accountId());
        assertEquals(1, newState.authorisations().size());
        assertEquals("tx1", newState.authorisations().get(0).transactionId());
        assertEquals(200, newState.authorisations().get(0).amount());
        assertEquals("auth123", newState.authorisations().get(0).authCode());
        assertEquals(800, newState.availableBalance()); // 1000 - 200
        assertEquals(500, newState.postedBalance()); // unchanged
    }

    @Test
    public void testOnAuthorisationAddedWithExistingAuthorisations() {
        var existingAuth = new AccountState.Authorisation("tx0", 100, "auth0");
        AccountState state = new AccountState("account123", List.of(existingAuth), 900, 500);
        AccountEvent.TransAuthorisationAdded event = new AccountEvent.TransAuthorisationAdded("tx1", 150, "auth1");
        
        AccountState newState = state.onAuthorisationAdded(event);
        
        assertEquals("account123", newState.accountId());
        assertEquals(2, newState.authorisations().size());
        assertEquals("tx0", newState.authorisations().get(0).transactionId());
        assertEquals("tx1", newState.authorisations().get(1).transactionId());
        assertEquals(750, newState.availableBalance()); // 900 - 150
        assertEquals(500, newState.postedBalance()); // unchanged
    }

    @Test
    public void testOnCaptureAdded() {
        var auth = new AccountState.Authorisation("tx1", 200, "auth1");
        AccountState state = new AccountState("account123", List.of(auth), 800, 500);
        AccountEvent.TransCaptureAdded event = new AccountEvent.TransCaptureAdded("tx1", 200);
        
        AccountState newState = state.onCaptureAdded(event);
        
        assertEquals("account123", newState.accountId());
        assertEquals(0, newState.authorisations().size()); // authorization removed after capture
        assertEquals(800, newState.availableBalance()); // unchanged
        assertEquals(300, newState.postedBalance()); // 500 - 200
    }


    @Test
    public void testAccountStateImmutability() {
        var originalAuth = new AccountState.Authorisation("tx0", 100, "auth0");
        AccountState originalState = new AccountState("account123", List.of(originalAuth), 900, 500);
        AccountEvent.TransAuthorisationAdded event = new AccountEvent.TransAuthorisationAdded("tx1", 200, "auth1");
        
        AccountState newState = originalState.onAuthorisationAdded(event);
        
        assertNotSame(originalState, newState);
        assertEquals(1, originalState.authorisations().size());
        assertEquals(2, newState.authorisations().size());
        assertEquals(900, originalState.availableBalance());
        assertEquals(700, newState.availableBalance());
    }

    @Test
    public void testAuthorisationRecord() {
        AccountState.Authorisation auth = new AccountState.Authorisation("tx123", 250, "auth789");
        
        assertEquals("tx123", auth.transactionId());
        assertEquals(250, auth.amount());
        assertEquals("auth789", auth.authCode());
    }

    @Test
    public void testIsAvailableBalance() {
        AccountState state = new AccountState("account123", List.of(), 1000, 500);
        
        assertTrue(state.isAvailableBalance(500));
        assertTrue(state.isAvailableBalance(1000));
        assertFalse(state.isAvailableBalance(1001));
    }

    @Test
    public void testGetAuthorisation() {
        var auth1 = new AccountState.Authorisation("tx1", 200, "auth1");
        var auth2 = new AccountState.Authorisation("tx2", 150, "auth2");
        AccountState state = new AccountState("account123", List.of(auth1, auth2), 650, 500);
        
        var foundAuth = state.getAuthorisation("tx1");
        assertTrue(foundAuth.isPresent());
        assertEquals("tx1", foundAuth.get().transactionId());
        assertEquals(200, foundAuth.get().amount());
        assertEquals("auth1", foundAuth.get().authCode());
        
        var notFoundAuth = state.getAuthorisation("tx999");
        assertTrue(notFoundAuth.isEmpty());
    }

    @Test
    public void testCompleteTransactionFlow() {
        // Start with empty account
        AccountState state = AccountState.empty();
        
        // Create account
        AccountEvent.Created createEvent = new AccountEvent.Created("account123", 1000);
        state = state.onCreate(createEvent);
        assertEquals("account123", state.accountId());
        assertEquals(1000, state.availableBalance());
        assertEquals(1000, state.postedBalance());
        
        // Initial balance is now set from the create event
        
        // Add authorization
        AccountEvent.TransAuthorisationAdded authEvent = new AccountEvent.TransAuthorisationAdded("tx1", 300, "auth1");
        state = state.onAuthorisationAdded(authEvent);
        assertEquals(700, state.availableBalance()); // 1000 - 300
        assertEquals(1000, state.postedBalance());
        assertEquals(1, state.authorisations().size());
        
        // Add capture
        AccountEvent.TransCaptureAdded captureEvent = new AccountEvent.TransCaptureAdded("tx1", 300);
        state = state.onCaptureAdded(captureEvent);
        assertEquals(700, state.availableBalance());
        assertEquals(700, state.postedBalance()); // 1000 - 300 (posted balance updated on capture)
        assertEquals(0, state.authorisations().size()); // authorization removed after capture
    }
}