package com.example.akka.payments.application;

import akka.grpc.GrpcClientSettings;
import akka.javasdk.DependencyProvider;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import com.example.akka.account.api.AccountGrpcEndpointClient;
import com.example.akka.account.api.AuthorizeTransactionResponse;
import com.example.akka.account.api.AuthResult;
import com.example.akka.account.api.AuthStatus;
import com.example.akka.payments.api.Card;
import com.example.akka.payments.api.CardGrpcEndpointClient;
import com.example.akka.payments.domain.TransactionState;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.wiremock.grpc.GrpcExtensionFactory;
import org.wiremock.grpc.dsl.WireMockGrpcService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
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

        return TestKit.Settings.DEFAULT.withDependencyProvider(mockDependencyProvider);
    }

    @BeforeEach
    public void setupWireMock() {

        var host = "localhost";
        var port = 8089;
        // Start WireMock server on port 8089 to mock AccountGrpcEndpointClient  
        wireMockServer = new WireMockServer(wireMockConfig()
            .port(port)
            .extensions(new GrpcExtensionFactory())
            .disableRequestJournal()); // Disable file-based features
        wireMockServer.start();
        WireMock.configureFor(host, port);
        
        // Create gRPC service wrapper for cleaner stubbing
        mockAccountService = new WireMockGrpcService(new WireMock(port), "com.example.akka.account.api.AccountGrpcEndpoint");
        
        mockAccountClient = AccountGrpcEndpointClient.create(GrpcClientSettings.connectToServiceAt(host,port,testKit.getActorSystem()),testKit.getActorSystem());
    }

    @AfterEach
    public void teardownWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    public void testTransactionWorkflowWithMockedAccountService() {
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

        var request = new TransactionWorkflow.StartTransactionRequest(
                "test-mocked-account-123",
                "txn-mocked",
                "4111111111111117",
                "12/25",
                "456",
                1500,
                "USD"
        );

        var startResult = workflowClient
                .method(TransactionWorkflow::startTransaction)
                .invoke(request);

        assertEquals(TransactionWorkflow.StartTransactionResult.STARTED, startResult);

        // Note: You'll need to handle the injection of the mocked AccountGrpcEndpointClient
        // so it points to localhost:8089 instead of the real corebanking service
        
        // Verify WireMock was called (once injection is set up)
        // verify(postRequestedFor(urlEqualTo("/com.example.akka.account.api.AccountGrpcEndpoint/AuthorizeTransaction")));
    }

    @Test
    public void testTransactionWorkflowWithInvalidCard() {
        // Don't create a card, so validation should fail

        // Get workflow client
        var workflowClient = componentClient.forWorkflow("test-invalid-card-123");

        // Start transaction with non-existent card
        var request = new TransactionWorkflow.StartTransactionRequest(
                "test-invalid-card-123",
                "txn-789",
                "9999999999999999",
                "12/25",
                "123",
                1000,
                "USD"
        );

        var startResult = workflowClient
                .method(TransactionWorkflow::startTransaction)
                .invoke(request);

        assertEquals(TransactionWorkflow.StartTransactionResult.STARTED, startResult);

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

    @Test
    public void testStartTransactionTwiceReturnsSameMessage() {
        var workflowClient = componentClient.forWorkflow("test-duplicate-123");

        var request = new TransactionWorkflow.StartTransactionRequest(
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
                .method(TransactionWorkflow::startTransaction)
                .invoke(request);

        assertEquals(TransactionWorkflow.StartTransactionResult.STARTED, firstResult);

        // Second call should return that transaction already exists
        var secondResult = workflowClient
                .method(TransactionWorkflow::startTransaction)
                .invoke(request);

        assertEquals(TransactionWorkflow.StartTransactionResult.ALREADY_EXISTS, secondResult);
    }

    @Test
    public void testWorkflowInitialState() {
        var workflowClient = componentClient.forWorkflow("test-initial-state");

        var request = new TransactionWorkflow.StartTransactionRequest(
                "test-initial-state",
                "txn-initial",
                "4111111111111114",
                "12/25",
                "123",
                2000,
                "EUR"
        );

        var startResult = workflowClient
                .method(TransactionWorkflow::startTransaction)
                .invoke(request);

        assertEquals(TransactionWorkflow.StartTransactionResult.STARTED, startResult);

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

    @Test
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

        var request = new TransactionWorkflow.StartTransactionRequest(
                "test-valid-card-123",
                "txn-valid",
                "4111111111111115",
                "12/25",
                "456",
                1500,
                "USD"
        );

        var startResult = workflowClient
                .method(TransactionWorkflow::startTransaction)
                .invoke(request);

        assertEquals(TransactionWorkflow.StartTransactionResult.STARTED, startResult);

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

    @Test
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

        var request = new TransactionWorkflow.StartTransactionRequest(
                "test-wrong-card-123",
                "txn-wrong",
                "4111111111111116",
                "12/25",
                "000", // Wrong CVV
                1500,
                "USD"
        );

        var startResult = workflowClient
                .method(TransactionWorkflow::startTransaction)
                .invoke(request);

        assertEquals(TransactionWorkflow.StartTransactionResult.STARTED, startResult);

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
}