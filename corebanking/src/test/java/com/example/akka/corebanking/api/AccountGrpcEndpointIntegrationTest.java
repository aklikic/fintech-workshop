package com.example.akka.corebanking.api;

import akka.javasdk.testkit.TestKitSupport;
import com.example.akka.account.api.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AccountGrpcEndpointIntegrationTest extends TestKitSupport {

    @Test
    public void testCreateAccount() {
        var client = getGrpcEndpointClient(AccountGrpcEndpointClient.class);
        
        var accountRequest = CreateAccountRequest.newBuilder()
                .setAccountId("account123")
                .setInitialBalance(1000)
                .build();
        
        var response = client.createAccount().invoke(accountRequest);
        
        assertEquals("account123", response.getAccountId());
        assertEquals(1000, response.getAvailableBalance());
        assertEquals(1000, response.getPostedBalance());
    }

    @Test
    public void testCreateAccountTwiceReturnsSameAccount() {
        var client = getGrpcEndpointClient(AccountGrpcEndpointClient.class);
        
        var accountRequest = CreateAccountRequest.newBuilder()
                .setAccountId("duplicate_account")
                .setInitialBalance(500)
                .build();
        
        var firstResponse = client.createAccount().invoke(accountRequest);
        var secondResponse = client.createAccount().invoke(accountRequest);
        
        assertEquals(firstResponse.getAccountId(), secondResponse.getAccountId());
        assertEquals(firstResponse.getAvailableBalance(), secondResponse.getAvailableBalance());
        assertEquals(firstResponse.getPostedBalance(), secondResponse.getPostedBalance());
    }

    @Test
    public void testGetAccount() {
        var client = getGrpcEndpointClient(AccountGrpcEndpointClient.class);
        
        var createRequest = CreateAccountRequest.newBuilder()
                .setAccountId("get_test_account")
                .setInitialBalance(750)
                .build();
        
        client.createAccount().invoke(createRequest);
        
        var getRequest = GetAccountRequest.newBuilder()
                .setAccountId("get_test_account")
                .build();
        
        var response = client.getAccount().invoke(getRequest);
        
        assertEquals("get_test_account", response.getAccountId());
        assertEquals(750, response.getAvailableBalance());
        assertEquals(750, response.getPostedBalance());
    }

    @Test
    public void testGetNonExistentAccount() {
        var client = getGrpcEndpointClient(AccountGrpcEndpointClient.class);
        
        var getRequest = GetAccountRequest.newBuilder()
                .setAccountId("non_existent_account")
                .build();
        
        assertThrows(Exception.class, () -> {
            client.getAccount().invoke(getRequest);
        });
    }

    @Test
    public void testAuthorizeTransaction() {
        var client = getGrpcEndpointClient(AccountGrpcEndpointClient.class);
        
        var createRequest = CreateAccountRequest.newBuilder()
                .setAccountId("auth_test_account")
                .setInitialBalance(1500)
                .build();
        
        client.createAccount().invoke(createRequest);
        
        var authRequest = AuthorizeTransactionRequest.newBuilder()
                .setAccountId("auth_test_account")
                .setTransactionId("txn123")
                .setAmount(100)
                .build();
        
        var response = client.authorizeTransaction().invoke(authRequest);
        
        assertNotNull(response.getAuthResult());
        assertNotNull(response.getAuthStatus());
    }

    @Test
    public void testAuthorizeTransactionForNonExistentAccount() {
        var client = getGrpcEndpointClient(AccountGrpcEndpointClient.class);
        
        var authRequest = AuthorizeTransactionRequest.newBuilder()
                .setAccountId("non_existent_auth_account")
                .setTransactionId("txn456")
                .setAmount(50)
                .build();
        
        var response = client.authorizeTransaction().invoke(authRequest);
        
        assertEquals("DECLINED", response.getAuthResult().toString());
        assertEquals("ACCOUNT_NOT_FOUND", response.getAuthStatus().toString());
        assertEquals("", response.getAuthCode());
    }

    @Test
    public void testCaptureTransaction() {
        var client = getGrpcEndpointClient(AccountGrpcEndpointClient.class);
        
        var createRequest = CreateAccountRequest.newBuilder()
                .setAccountId("capture_test_account")
                .setInitialBalance(2000)
                .build();
        
        client.createAccount().invoke(createRequest);
        
        // First authorize a transaction
        var authRequest = AuthorizeTransactionRequest.newBuilder()
                .setAccountId("capture_test_account")
                .setTransactionId("txn789")
                .setAmount(75)
                .build();
        
        client.authorizeTransaction().invoke(authRequest);
        
        // Then capture it
        var captureRequest = CaptureTransactionRequest.newBuilder()
                .setAccountId("capture_test_account")
                .setTransactionId("txn789")
                .build();
        
        var response = client.captureTransaction().invoke(captureRequest);
        
        assertTrue(response.getSuccess());
    }

    @Test
    public void testCaptureTransactionForNonExistentAccount() {
        var client = getGrpcEndpointClient(AccountGrpcEndpointClient.class);
        
        var captureRequest = CaptureTransactionRequest.newBuilder()
                .setAccountId("non_existent_capture_account")
                .setTransactionId("txn999")
                .build();
        
        assertThrows(Exception.class, () -> {
            client.captureTransaction().invoke(captureRequest);
        });
    }
}