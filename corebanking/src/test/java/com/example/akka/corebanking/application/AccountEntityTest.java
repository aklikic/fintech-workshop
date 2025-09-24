package com.example.akka.corebanking.application;

import akka.Done;
import akka.javasdk.testkit.EventSourcedTestKit;
import com.example.akka.corebanking.application.AccountEntity;
import com.example.akka.corebanking.domain.AccountEvent;
import com.example.akka.corebanking.domain.AccountState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AccountEntityTest {

    @Test
    public void testCreateAccountWhenEmpty() {
        var testKit = EventSourcedTestKit.of(AccountEntity::new);
        
        var accountRequest = new AccountEntity.ApiAccount("account123", 1000, 1000);
        var result = testKit.method(AccountEntity::createAccount).invoke(accountRequest);
        
        assertEquals("account123", result.getReply().accountId());
        assertEquals(1000, result.getReply().availableBalance());
        assertEquals(1000, result.getReply().postedBalance());
        
        var createdEvent = result.getNextEventOfType(AccountEvent.Created.class);
        assertEquals("account123", createdEvent.accountId());

        var state = (AccountState)result.getUpdatedState();
        assertFalse(state.isEmpty());
        assertEquals("account123", state.accountId());
        assertEquals(1000, state.availableBalance());
        assertEquals(1000, state.postedBalance());
        assertTrue(state.authorisations().isEmpty());
    }

    @Test
    public void testCreateAccountWhenAccountAlreadyExists() {
        var testKit = EventSourcedTestKit.of(AccountEntity::new);
        
        var existingAccount = new AccountEntity.ApiAccount("account123", 500, 500);
        testKit.method(AccountEntity::createAccount).invoke(existingAccount);
        
        var newAccountRequest = new AccountEntity.ApiAccount("account456", 100, 50);
        var result = testKit.method(AccountEntity::createAccount).invoke(newAccountRequest);
        
        assertEquals("account123", result.getReply().accountId());
        assertEquals(500, result.getReply().availableBalance());
        assertEquals(500, result.getReply().postedBalance());
        
        assertFalse(result.didPersistEvents());
    }

    @Test
    public void testGetAccountWhenExists() {
        var testKit = EventSourcedTestKit.of(AccountEntity::new);
        
        var accountRequest = new AccountEntity.ApiAccount("account789", 750, 750);
        testKit.method(AccountEntity::createAccount).invoke(accountRequest);
        
        var result = testKit.method(AccountEntity::getAccount).invoke();
        
        assertEquals("account789", result.getReply().accountId());
        assertEquals(750, result.getReply().availableBalance());
        assertEquals(750, result.getReply().postedBalance());
        
        assertFalse(result.didPersistEvents());
    }

    @Test
    public void testGetAccountWhenNotExists() {
        var testKit = EventSourcedTestKit.of(AccountEntity::new);
        
        var result = testKit.method(AccountEntity::getAccount).invoke();
        
        assertTrue(result.isError());
        assertEquals("Account not found", result.getError());
    }

    @Test
    public void testAuthoriseTransactionAccountNotFound() {
        var testKit = EventSourcedTestKit.of(AccountEntity::new);
        
        var authRequest = new AccountEntity.AuthorisationRequest("tx123", 200);
        var result = testKit.method(AccountEntity::authoriseTransaction).invoke(authRequest);
        
        assertTrue(result.getReply().authCode().isEmpty());
        assertEquals(AccountEntity.AuthorisationResult.declined, result.getReply().authResult());
        assertEquals(AccountEntity.AuthorisationStatus.account_not_found, result.getReply().authStatus());
        
        assertFalse(result.didPersistEvents());
    }

    @Test
    public void testAuthoriseTransactionInsufficientFunds() {
        var testKit = EventSourcedTestKit.of(AccountEntity::new);
        
        // Create account with limited balance
        var accountRequest = new AccountEntity.ApiAccount("account123", 100, 100);
        testKit.method(AccountEntity::createAccount).invoke(accountRequest);
        
        // Test with insufficient balance trying to authorize
        var authRequest = new AccountEntity.AuthorisationRequest("tx123", 200);
        var result = testKit.method(AccountEntity::authoriseTransaction).invoke(authRequest);
        
        assertTrue(result.getReply().authCode().isEmpty());
        assertEquals(AccountEntity.AuthorisationResult.declined, result.getReply().authResult());
        assertEquals(AccountEntity.AuthorisationStatus.insufficient_funds, result.getReply().authStatus());
        
        assertFalse(result.didPersistEvents());
    }

    @Test
    public void testCaptureTransactionAccountNotFound() {
        var testKit = EventSourcedTestKit.of(AccountEntity::new);
        
        var result = testKit.method(AccountEntity::captureTransaction).invoke("tx123");
        
        assertTrue(result.isError());
        assertEquals("Account not found", result.getError());
    }

    @Test
    public void testCaptureTransactionWhenNoAuthorization() {
        var testKit = EventSourcedTestKit.of(AccountEntity::new);
        
        // Create account but no authorization
        var accountRequest = new AccountEntity.ApiAccount("account123", 300, 300);
        testKit.method(AccountEntity::createAccount).invoke(accountRequest);
        
        var result = testKit.method(AccountEntity::captureTransaction).invoke("tx123");
        
        assertEquals(Done.getInstance(), result.getReply());
        assertFalse(result.didPersistEvents()); // Should be deduplicated
    }

    @Test
    public void testAuthoriseTransactionDuplicateRequestReturnsSuccess() {
        var testKit = EventSourcedTestKit.of(AccountEntity::new);
        
        // Create account with sufficient balance
        var accountRequest = new AccountEntity.ApiAccount("account123", 1000, 1000);
        testKit.method(AccountEntity::createAccount).invoke(accountRequest);
        
        // First authorization request
        var authRequest = new AccountEntity.AuthorisationRequest("tx123", 200);
        var firstResult = testKit.method(AccountEntity::authoriseTransaction).invoke(authRequest);
        
        // Verify first authorization succeeds
        assertTrue(firstResult.getReply().authCode().isPresent());
        assertEquals(AccountEntity.AuthorisationResult.authorised, firstResult.getReply().authResult());
        assertEquals(AccountEntity.AuthorisationStatus.ok, firstResult.getReply().authStatus());
        String firstAuthCode = firstResult.getReply().authCode().get();
        
        // Second authorization request with same transaction ID (duplicate)
        var secondResult = testKit.method(AccountEntity::authoriseTransaction).invoke(authRequest);
        
        // Verify second authorization also succeeds with same auth code (deduplication)
        assertTrue(secondResult.getReply().authCode().isPresent());
        assertEquals(AccountEntity.AuthorisationResult.authorised, secondResult.getReply().authResult());
        assertEquals(AccountEntity.AuthorisationStatus.ok, secondResult.getReply().authStatus());
        assertEquals(firstAuthCode, secondResult.getReply().authCode().get());
        
        // Verify no additional events were persisted for the duplicate
        assertFalse(secondResult.didPersistEvents());
        
        // Verify only one authorization exists in state
        var finalState = (AccountState)secondResult.getUpdatedState();
        assertEquals(1, finalState.authorisations().size());
        assertEquals("tx123", finalState.authorisations().get(0).transactionId());
    }

    @Test
    public void testCaptureTransactionDuplicateRequestReturnsSuccess() {
        var testKit = EventSourcedTestKit.of(AccountEntity::new);
        
        // Create account with sufficient balance
        var accountRequest = new AccountEntity.ApiAccount("account123", 1000, 1000);
        testKit.method(AccountEntity::createAccount).invoke(accountRequest);
        
        // First authorize a transaction
        var authRequest = new AccountEntity.AuthorisationRequest("tx123", 200);
        var authResult = testKit.method(AccountEntity::authoriseTransaction).invoke(authRequest);
        
        // Verify authorization succeeded
        assertTrue(authResult.getReply().authCode().isPresent());
        assertEquals(AccountEntity.AuthorisationResult.authorised, authResult.getReply().authResult());
        
        // Verify account balances after authorization
        var stateAfterAuth = (AccountState)authResult.getUpdatedState();
        assertEquals(800, stateAfterAuth.availableBalance()); // 1000 - 200
        assertEquals(1000, stateAfterAuth.postedBalance()); // unchanged
        assertEquals(1, stateAfterAuth.authorisations().size());
        
        // First capture
        var firstCaptureResult = testKit.method(AccountEntity::captureTransaction).invoke("tx123");
        
        // Verify first capture succeeds
        assertEquals(Done.getInstance(), firstCaptureResult.getReply());
        assertTrue(firstCaptureResult.didPersistEvents());
        
        // Verify account balances after first capture
        var stateAfterFirstCapture = (AccountState)firstCaptureResult.getUpdatedState();
        assertEquals(800, stateAfterFirstCapture.availableBalance()); // unchanged
        assertEquals(800, stateAfterFirstCapture.postedBalance()); // 1000 - 200 (captured)
        assertEquals(0, stateAfterFirstCapture.authorisations().size()); // authorization removed
        
        // Second capture (duplicate)
        var secondCaptureResult = testKit.method(AccountEntity::captureTransaction).invoke("tx123");
        
        // Verify second capture also succeeds (deduplication)
        assertEquals(Done.getInstance(), secondCaptureResult.getReply());
        assertFalse(secondCaptureResult.didPersistEvents()); // No events persisted for duplicate
        
        // Verify account balances remain unchanged after duplicate capture
        var finalState = (AccountState)secondCaptureResult.getUpdatedState();
        assertEquals(800, finalState.availableBalance());
        assertEquals(800, finalState.postedBalance());
        assertEquals(0, finalState.authorisations().size());
    }

    @Test
    public void testEmptyState() {
        var testKit = EventSourcedTestKit.of(AccountEntity::new);
        
        var state = testKit.getState();
        assertTrue(state.isEmpty());
        assertEquals("", state.accountId());
        assertEquals(0, state.availableBalance());
        assertEquals(0, state.postedBalance());
        assertTrue(state.authorisations().isEmpty());
    }
}