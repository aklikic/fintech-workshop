package com.example.akka.payments.application;

import akka.grpc.GrpcClientSettings;
import akka.javasdk.DependencyProvider;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import com.example.akka.account.api.*;
import com.example.akka.payments.api.Card;
import com.example.akka.payments.api.CardGrpcEndpointClient;
import com.example.akka.payments.domain.TransactionState;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.*;
import org.wiremock.grpc.GrpcExtensionFactory;
import org.wiremock.grpc.dsl.WireMockGrpcService;

import java.time.Duration;
import java.util.Set;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;
import static org.wiremock.grpc.dsl.WireMockGrpc.*;

public class TransactionWorkflowTest extends TestKitSupport {

    private WireMockServer wireMockServer;
    private AccountGrpcEndpointClient mockAccountClient;
    private WireMockGrpcService mockAccountService;

    @Override
    protected TestKit.Settings testKitSettings() {
         // Create custom DependencyProvider that provides mocked AccountGrpcEndpointClient
        DependencyProvider mockDependencyProvider = new DependencyProvider() {
            @SuppressWarnings("unchecked")
            @Override
            public <T> T getDependency(Class<T> clazz) {
                if (clazz.equals(AccountGrpcEndpointClient.class)) {
                    return (T) mockAccountClient;
                } else {
                    return null; // Use default dependencies for other types
                }
            }
        };

        return TestKit.Settings.DEFAULT
                .withDependencyProvider(mockDependencyProvider)
                .withDisabledComponents(Set.of(CorebankingServiceEventConsumer.class));
    }

    @BeforeAll
    public void setupWireMock() {

        var host = "localhost";
//        var port = 8089;
        // Start WireMock server on port 8089 to mock AccountGrpcEndpointClient  
        wireMockServer = new WireMockServer(wireMockConfig()
                .dynamicPort()
                .withRootDirectory("src/test/resources/wiremock")
                .extensions(new GrpcExtensionFactory())); // Enable gRPC support
        wireMockServer.start();
//        WireMock.configureFor(host, port);
        
        // Create gRPC service wrapper for cleaner stubbing
        mockAccountService = new WireMockGrpcService(new WireMock(wireMockServer.port()), "com.example.akka.account.api.AccountGrpcEndpoint");
        
        mockAccountClient = AccountGrpcEndpointClient.create(
            GrpcClientSettings.connectToServiceAt(host,wireMockServer.port(),testKit.getActorSystem()).withTls(false),
            testKit.getActorSystem());
    }

    @AfterAll
    public void teardownWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

//    @Test
    public void testTransactionWorkflowWithMockedAccountService() throws Exception {
        // Setup WireMock gRPC service to return successful authorization
        mockAccountService.stubFor(
            method("AuthorizeTransaction")
                .willReturn(message(AuthorizeTransactionResponse.newBuilder()
                    .setAuthCode("AUTH123")
                    .setAuthResult(AuthResult.AUTHORISED)
                    .setAuthStatus(AuthStatus.OK)
                    .build()))
        );

        // Create a valid card first
        var cardClient = getGrpcEndpointClient(CardGrpcEndpointClient.class);
        var card = Card.newBuilder()
                .setPan("4111111111111117")
                .setExpiryDate("12/25")
                .setCvv("456")
                .setAccountId("account-test")
                .build();
        cardClient.createCard().invoke(card);

        // Start workflow with the valid card
        var workflowClient = componentClient.forWorkflow("test-mocked-account-123");

        var request = new TransactionWorkflow.AuthorizeTransactionRequest(
                "test-mocked-account-123",
                "txn-mocked",
                "4111111111111117",
                "12/25",
                "456",
                1500,
                "USD"
        );

        var startResult = workflowClient
                .method(TransactionWorkflow::authorizeTransaction)
                .invoke(request);

        assertEquals(TransactionWorkflow.StartAuthorizeTransactionResult.STARTED, startResult);


        Thread.sleep(2000);

        var state =  workflowClient
                .method(TransactionWorkflow::getTransaction)
                .invoke();

        assertNotNull(state);
        assertEquals("test-mocked-account-123", state.idempotencyKey());
        assertEquals("txn-mocked", state.transactionId());
        assertEquals("AUTH123", state.authCode());
        assertEquals(TransactionState.AuthResult.authorised, state.authResult());
        assertEquals(TransactionState.AuthStatus.ok, state.authStatus());

        // Note: You'll need to handle the injection of the mocked AccountGrpcEndpointClient
        // so it points to localhost:8089 instead of the real corebanking service
        
        // Verify WireMock was called (once injection is set up)
        // verify(postRequestedFor(urlEqualTo("/com.example.akka.account.api.AccountGrpcEndpoint/AuthorizeTransaction")));
    }

//    @Test
    public void testTransactionWorkflowWithInvalidCard() {
        // Don't create a card, so validation should fail

        // Get workflow client
        var workflowClient = componentClient.forWorkflow("test-invalid-card-123");

        // Start transaction with non-existent card
        var request = new TransactionWorkflow.AuthorizeTransactionRequest(
                "test-invalid-card-123",
                "txn-789",
                "9999999999999999",
                "12/25",
                "123",
                1000,
                "USD"
        );

        var startResult = workflowClient
                .method(TransactionWorkflow::authorizeTransaction)
                .invoke(request);

        assertEquals(TransactionWorkflow.StartAuthorizeTransactionResult.STARTED, startResult);

        // Wait for workflow to complete and check final state
        var state = await(
            workflowClient
                .method(TransactionWorkflow::getTransaction)
                .invokeAsync(),
            Duration.ofSeconds(10)
        );
        
        assertNotNull(state);
        assertEquals("test-invalid-card-123", state.idempotencyKey());
        assertEquals("txn-789", state.transactionId());
        assertEquals("", state.authCode());
        assertEquals(TransactionState.AuthResult.declined, state.authResult());
        assertEquals(TransactionState.AuthStatus.card_not_found, state.authStatus());
    }

//    @Test
    public void testAuthoriseTwiceReturnsSameMessage() {
        var workflowClient = componentClient.forWorkflow("test-duplicate-123");

        var request = new TransactionWorkflow.AuthorizeTransactionRequest(
                "test-duplicate-123",
                "txn-duplicate",
                "4111111111111113",
                "12/25",
                "123",
                1000,
                "USD"
        );

        // First call should start the workflow
        var firstResult = workflowClient
                .method(TransactionWorkflow::authorizeTransaction)
                .invoke(request);

        assertEquals(TransactionWorkflow.StartAuthorizeTransactionResult.STARTED, firstResult);

        // Second call should return that transaction already exists
        var secondResult = workflowClient
                .method(TransactionWorkflow::authorizeTransaction)
                .invoke(request);

        assertEquals(TransactionWorkflow.StartAuthorizeTransactionResult.ALREADY_EXISTS, secondResult);
    }

//    @Test
    public void testWorkflowInitialState() {
        var workflowClient = componentClient.forWorkflow("test-initial-state");

        var request = new TransactionWorkflow.AuthorizeTransactionRequest(
                "test-initial-state",
                "txn-initial",
                "4111111111111114",
                "12/25",
                "123",
                2000,
                "EUR"
        );

        var startResult = workflowClient
                .method(TransactionWorkflow::authorizeTransaction)
                .invoke(request);

        assertEquals(TransactionWorkflow.StartAuthorizeTransactionResult.STARTED, startResult);

        // Check that the workflow state is properly initialized
        var state = await(
            workflowClient
                .method(TransactionWorkflow::getTransaction)
                .invokeAsync(),
            Duration.ofSeconds(10)
        );
        
        assertNotNull(state);
        assertEquals("test-initial-state", state.idempotencyKey());
        assertEquals("txn-initial", state.transactionId());
        assertEquals("4111111111111114", state.cardData().cardPan());
        assertEquals("12/25", state.cardData().cardExpiryDate());
        assertEquals("123", state.cardData().cardCvv());
        assertEquals(2000, state.cardData().amount());
        assertEquals("EUR", state.cardData().currency());
    }

//    @Test
    public void testWorkflowCardValidationWithValidCard() {
        // Create a valid card first
        var cardClient = getGrpcEndpointClient(CardGrpcEndpointClient.class);
        var card = Card.newBuilder()
                .setPan("4111111111111115")
                .setExpiryDate("12/25")
                .setCvv("456")
                .setAccountId("account-test")
                .build();
        cardClient.createCard().invoke(card);

        // Start workflow with the valid card
        var workflowClient = componentClient.forWorkflow("test-valid-card-123");

        var request = new TransactionWorkflow.AuthorizeTransactionRequest(
                "test-valid-card-123",
                "txn-valid",
                "4111111111111115",
                "12/25",
                "456",
                1500,
                "USD"
        );

        var startResult = workflowClient
                .method(TransactionWorkflow::authorizeTransaction)
                .invoke(request);

        assertEquals(TransactionWorkflow.StartAuthorizeTransactionResult.STARTED, startResult);

        // The workflow should proceed to authorization step
        // Note: Without mocking AccountGrpcEndpointClient, the authorization will likely fail
        // But we can verify the workflow reaches that point and doesn't fail on card validation
        var state = await(
            workflowClient
                .method(TransactionWorkflow::getTransaction)
                .invokeAsync(),
            Duration.ofSeconds(10)
        );
        
        assertNotNull(state);
        assertEquals("test-valid-card-123", state.idempotencyKey());
        assertEquals("txn-valid", state.transactionId());
        // Card validation should pass, so we should have proceeded past the validate-card step
    }

//    @Test
    public void testWorkflowCardValidationWithWrongCardDetails() {
        // Create a card with specific details
        var cardClient = getGrpcEndpointClient(CardGrpcEndpointClient.class);
        var card = Card.newBuilder()
                .setPan("4111111111111116")
                .setExpiryDate("12/25")
                .setCvv("789")
                .setAccountId("account-wrong")
                .build();
        cardClient.createCard().invoke(card);

        // Start workflow with wrong card details (different CVV)
        var workflowClient = componentClient.forWorkflow("test-wrong-card-123");

        var request = new TransactionWorkflow.AuthorizeTransactionRequest(
                "test-wrong-card-123",
                "txn-wrong",
                "4111111111111116",
                "12/25",
                "000", // Wrong CVV
                1500,
                "USD"
        );

        var startResult = workflowClient
                .method(TransactionWorkflow::authorizeTransaction)
                .invoke(request);

        assertEquals(TransactionWorkflow.StartAuthorizeTransactionResult.STARTED, startResult);

        // Card validation should fail due to wrong CVV
        var state = await(
            workflowClient
                .method(TransactionWorkflow::getTransaction)
                .invokeAsync(),
            Duration.ofSeconds(10)
        );
        
        assertNotNull(state);
        assertEquals("test-wrong-card-123", state.idempotencyKey());
        assertEquals("txn-wrong", state.transactionId());
        assertEquals("", state.authCode());
        assertEquals(TransactionState.AuthResult.declined, state.authResult());
        assertEquals(TransactionState.AuthStatus.card_not_found, state.authStatus());
    }

//    @Test
    public void testCaptureTransactionAfterAuthorization() {
        // Setup WireMock gRPC service to return successful authorization and capture
        mockAccountService.stubFor(
            method("AuthorizeTransaction")
                .willReturn(message(AuthorizeTransactionResponse.newBuilder()
                    .setAuthCode("AUTH789")
                    .setAuthResult(AuthResult.AUTHORISED)
                    .setAuthStatus(AuthStatus.OK)
                    .build()))
        );
        
        mockAccountService.stubFor(
            method("CaptureTransaction")
                .willReturn(message(CaptureTransactionResponse.newBuilder()
                    .setSuccess(true)
                    .build()))
        );

        // Create a valid card first
        var cardClient = getGrpcEndpointClient(CardGrpcEndpointClient.class);
        var card = Card.newBuilder()
                .setPan("4111111111111119")
                .setExpiryDate("12/25")
                .setCvv("789")
                .setAccountId("account-capture-test")
                .build();
        cardClient.createCard().invoke(card);

        // Start workflow with the valid card
        var workflowClient = componentClient.forWorkflow("test-capture-123");

        var request = new TransactionWorkflow.AuthorizeTransactionRequest(
                "test-capture-123",
                "txn-capture",
                "4111111111111119",
                "12/25",
                "789",
                2500,
                "USD"
        );

        var startResult = workflowClient
                .method(TransactionWorkflow::authorizeTransaction)
                .invoke(request);

        assertEquals(TransactionWorkflow.StartAuthorizeTransactionResult.STARTED, startResult);

        // Wait a bit for the workflow to start processing asynchronously
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Wait for workflow to reach authorization (and pause)
        var state = await(
            workflowClient
                .method(TransactionWorkflow::getTransaction)
                .invokeAsync(),
            Duration.ofSeconds(10)
        );
        
        // Verify transaction is authorized but not captured yet
        assertNotNull(state);
        assertEquals("test-capture-123", state.idempotencyKey());
        assertEquals("txn-capture", state.transactionId());
        assertEquals("AUTH789", state.authCode());
        assertEquals(TransactionState.AuthResult.authorised, state.authResult());
        assertEquals(TransactionState.AuthStatus.ok, state.authStatus());
        assertEquals("account-capture-test", state.accountId());
        assertFalse(state.captured());

        // Now trigger capture
        var captureResult = workflowClient
                .method(TransactionWorkflow::captureTransaction)
                .invoke();

        assertEquals(TransactionWorkflow.CaptureTransactionResult.CAPTURE_STARTED, captureResult);

        // Wait for capture to complete and verify final state
        var finalState = await(
            workflowClient
                .method(TransactionWorkflow::getTransaction)
                .invokeAsync(),
            Duration.ofSeconds(10)
        );
        
        assertNotNull(finalState);
        assertEquals("test-capture-123", finalState.idempotencyKey());
        assertEquals("txn-capture", finalState.transactionId());
        assertEquals("AUTH789", finalState.authCode());
        assertEquals(TransactionState.AuthResult.authorised, finalState.authResult());
        assertEquals(TransactionState.AuthStatus.ok, finalState.authStatus());
        assertEquals("account-capture-test", finalState.accountId());
        assertTrue(finalState.captured()); // Should now be captured
    }

//    @Test
    public void testCaptureTransactionNotAuthorized() {
        // Create a card but don't set up authorization mock (will fail)
        var cardClient = getGrpcEndpointClient(CardGrpcEndpointClient.class);
        var card = Card.newBuilder()
                .setPan("4111111111111120")
                .setExpiryDate("12/25")
                .setCvv("123")
                .setAccountId("account-not-auth")
                .build();
        cardClient.createCard().invoke(card);

        var workflowClient = componentClient.forWorkflow("test-capture-not-auth");

        var request = new TransactionWorkflow.AuthorizeTransactionRequest(
                "test-capture-not-auth",
                "txn-not-auth",
                "4111111111111120",
                "12/25",
                "123",
                1000,
                "USD"
        );

        workflowClient
                .method(TransactionWorkflow::authorizeTransaction)
                .invoke(request);

        // Wait for workflow to complete authorization (should fail)
        await(
            workflowClient
                .method(TransactionWorkflow::getTransaction)
                .invokeAsync(),
            Duration.ofSeconds(10)
        );

        // Try to capture - should fail since not authorized
        var captureResult = workflowClient
                .method(TransactionWorkflow::captureTransaction)
                .invoke();

        assertEquals(TransactionWorkflow.CaptureTransactionResult.NOT_AUTHORIZED, captureResult);
    }

//    @Test
    public void testCaptureTransactionAlreadyCaptured() {
        // Setup WireMock gRPC service to return successful authorization and capture
        mockAccountService.stubFor(
            method("AuthorizeTransaction")
                .willReturn(message(AuthorizeTransactionResponse.newBuilder()
                    .setAuthCode("AUTH456")
                    .setAuthResult(AuthResult.AUTHORISED)
                    .setAuthStatus(AuthStatus.OK)
                    .build()))
        );
        
        mockAccountService.stubFor(
            method("CaptureTransaction")
                .willReturn(message(CaptureTransactionResponse.newBuilder()
                    .setSuccess(true)
                    .build()))
        );

        // Create a valid card first
        var cardClient = getGrpcEndpointClient(CardGrpcEndpointClient.class);
        var card = Card.newBuilder()
                .setPan("4111111111111121")
                .setExpiryDate("12/25")
                .setCvv("456")
                .setAccountId("account-double-capture")
                .build();
        cardClient.createCard().invoke(card);

        var workflowClient = componentClient.forWorkflow("test-double-capture");

        var request = new TransactionWorkflow.AuthorizeTransactionRequest(
                "test-double-capture",
                "txn-double-capture",
                "4111111111111121",
                "12/25",
                "456",
                3000,
                "EUR"
        );

        workflowClient
                .method(TransactionWorkflow::authorizeTransaction)
                .invoke(request);

        // Wait a bit for the workflow to start processing asynchronously
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Wait for authorization
        await(
            workflowClient
                .method(TransactionWorkflow::getTransaction)
                .invokeAsync(),
            Duration.ofSeconds(10)
        );

        // First capture - should succeed
        var firstCaptureResult = workflowClient
                .method(TransactionWorkflow::captureTransaction)
                .invoke();

        assertEquals(TransactionWorkflow.CaptureTransactionResult.CAPTURE_STARTED, firstCaptureResult);

        // Wait for capture to complete
        await(
            workflowClient
                .method(TransactionWorkflow::getTransaction)
                .invokeAsync(),
            Duration.ofSeconds(10)
        );

        // Second capture - should fail as already captured
        var secondCaptureResult = workflowClient
                .method(TransactionWorkflow::captureTransaction)
                .invoke();

        assertEquals(TransactionWorkflow.CaptureTransactionResult.ALREADY_CAPTURED, secondCaptureResult);
    }

//    @Test
    public void testCaptureTransactionNotFound() {
        // Try to capture on non-existent workflow
        var workflowClient = componentClient.forWorkflow("non-existent-workflow");

        var captureResult = workflowClient
                .method(TransactionWorkflow::captureTransaction)
                .invoke();

        assertEquals(TransactionWorkflow.CaptureTransactionResult.TRANSACTION_NOT_FOUND, captureResult);
    }
}