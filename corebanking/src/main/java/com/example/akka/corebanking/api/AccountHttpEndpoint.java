package com.example.akka.corebanking.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import com.example.akka.corebanking.application.AccountEntity;
import com.example.akka.corebanking.application.AccountView;
import org.slf4j.Logger;

import java.util.List;

@HttpEndpoint("/accounts")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class AccountHttpEndpoint extends AbstractHttpEndpoint {

    private final static Logger logger = org.slf4j.LoggerFactory.getLogger(AccountHttpEndpoint.class);
    private final ComponentClient componentClient;

    public AccountHttpEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // Request/Response records
    public record CreateAccountRequest(String accountId, int initialBalance) {}

    public record AccountResponse(String accountId, int availableBalance, int postedBalance) {}

    public record AuthorizeTransactionRequest(String transactionId, int amount) {}

    public record AuthorizeTransactionResponse(String authCode, String authResult, String authStatus) {}

    public record TransactionRequest(String transactionId) {}

    public record CaptureTransactionResponse(String captureResult, String captureStatus) {}

    public record CancelTransactionResponse(String cancelResult, String cancelStatus) {}

    public record GetAllAccountsResponse(List<AccountResponse> accounts) {}

    /**
     * Creates a new account with the given account ID and initial balance.
     * POST /accounts
     */
    @Post
    public AccountResponse createAccount(CreateAccountRequest request) {
        logger.info("Creating account {}", request.accountId());
        try {
            var result = componentClient.forEventSourcedEntity(request.accountId())
                    .method(AccountEntity::createAccount)
                    .invoke(new AccountEntity.ApiAccount(request.accountId(), request.initialBalance(), request.initialBalance()));
            return fromState(result);
        } catch (Exception e) {
            logger.error("Failed to create account {}: {}", request.accountId(), e.getMessage());
            throw new RuntimeException("Failed to create account: " + e.getMessage());
        }
    }

    /**
     * Gets an account by ID.
     * GET /accounts/{accountId}
     */
    @Get("/{accountId}")
    public AccountResponse getAccount(String accountId) {
        logger.info("Getting account {}", accountId);
        try {
            var account = componentClient.forEventSourcedEntity(accountId)
                    .method(AccountEntity::getAccount)
                    .invoke();
            if (account.isEmpty()) {
                throw new IllegalArgumentException("Account not found: " + accountId);
            }
            return fromState(account);
        } catch (Exception e) {
            logger.error("Failed to get account {}: {}", accountId, e.getMessage());
            throw new RuntimeException("Failed to get account: " + e.getMessage());
        }
    }

    /**
     * Authorizes a transaction for an account.
     * POST /accounts/{accountId}/authorize
     */
    @Post("/{accountId}/authorize")
    public AuthorizeTransactionResponse authorizeTransaction(String accountId, AuthorizeTransactionRequest request) {
        logger.info("Authorizing transaction {} for account {} amount {}",
                request.transactionId(), accountId, request.amount());

        try {
            var authRequest = new AccountEntity.AuthorisationRequest(request.transactionId(), request.amount());
            var response = componentClient.forEventSourcedEntity(accountId)
                    .method(AccountEntity::authoriseTransaction)
                    .invoke(authRequest);

            return new AuthorizeTransactionResponse(
                    response.authCode().orElse(""),
                    response.authResult().toString(),
                    response.authStatus().toString());
        } catch (Exception e) {
            logger.error("Failed to authorize transaction {} for account {}: {}",
                    request.transactionId(), accountId, e.getMessage());
            throw new RuntimeException("Failed to authorize transaction: " + e.getMessage());
        }
    }

    /**
     * Captures a transaction for an account.
     * POST /accounts/{accountId}/capture
     */
    @Post("/{accountId}/capture")
    public CaptureTransactionResponse captureTransaction(String accountId, TransactionRequest request) {
        logger.info("Capturing transaction {} for account {}", request.transactionId(), accountId);

        try {
            var result = componentClient.forEventSourcedEntity(accountId)
                    .method(AccountEntity::captureTransaction)
                    .invoke(request.transactionId());

            return new CaptureTransactionResponse(
                    result.captureResult().toString(),
                    result.captureStatus().toString());
        } catch (Exception e) {
            logger.error("Failed to capture transaction {} for account {}: {}",
                    request.transactionId(), accountId, e.getMessage());
            throw new RuntimeException("Failed to capture transaction: " + e.getMessage());
        }
    }

    /**
     * Cancels a transaction for an account.
     * POST /accounts/{accountId}/cancel
     */
    @Post("/{accountId}/cancel")
    public CancelTransactionResponse cancelTransaction(String accountId, TransactionRequest request) {
        logger.info("Cancel transaction {} for account {}", request.transactionId(), accountId);

        try {
            var result = componentClient.forEventSourcedEntity(accountId)
                    .method(AccountEntity::cancelTransaction)
                    .invoke(request.transactionId());

            return new CancelTransactionResponse(
                    result.cancelResult().toString(),
                    result.cancelStatus().toString());
        } catch (Exception e) {
            logger.error("Failed to cancel transaction {} for account {}: {}",
                    request.transactionId(), accountId, e.getMessage());
            throw new RuntimeException("Failed to cancel transaction: " + e.getMessage());
        }
    }

    /**
     * Gets all accounts.
     * GET /accounts
     */
    @Get
    public GetAllAccountsResponse getAllAccounts() {
        logger.info("Getting all accounts");
        try {
            var accountList = componentClient.forView().method(AccountView::getAllAccounts).invoke();
            var accounts = accountList.accounts().stream()
                    .map(account -> new AccountResponse(
                            account.accountId(),
                            account.availableBalance(),
                            account.postedBalance()))
                    .toList();
            return new GetAllAccountsResponse(accounts);
        } catch (Exception e) {
            logger.error("Failed to get all accounts: {}", e.getMessage());
            throw new RuntimeException("Failed to get all accounts: " + e.getMessage());
        }
    }

    private AccountResponse fromState(AccountEntity.ApiAccount account) {
        return new AccountResponse(
                account.accountId(),
                account.availableBalance(),
                account.postedBalance());
    }
}