package com.example.akka.corebanking.api;

import akka.javasdk.JsonSupport;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.annotations.mcp.McpEndpoint;
import akka.javasdk.annotations.mcp.McpTool;
import akka.javasdk.client.ComponentClient;
import com.example.akka.corebanking.application.AccountEntity;
import com.example.akka.corebanking.application.AccountView;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@McpEndpoint(serverName = "account-mcp", serverVersion = "0.0.1")
public class AccountMcpEndpoint {

    private final ComponentClient componentClient;

    public AccountMcpEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @McpTool(description = "Create a new account")
    public String createAccount(CreateAccountRequest request) {
        componentClient.forEventSourcedEntity(request.accountId()).method(AccountEntity::createAccount).invoke(new AccountEntity.ApiAccount(request.accountId(), request.initialBalance(), request.initialBalance()));
        return "OK";
    }

    @McpTool(description = "Get account information")
    public String getAccount(String accountId) {
        var result = componentClient.forEventSourcedEntity(accountId).method(AccountEntity::getAccount).invoke();
        var res = new Account(result.accountId(), result.availableBalance(), result.postedBalance());
        return JsonSupport.encodeToString(res);
    }

    @McpTool(description = "Get list of all accounts")
    public String getAllAccounts() {
        var result = componentClient.forView().method(AccountView::getAllAccounts).invoke();
        var res = new GetAllAccountsResponse(result.accounts().stream().map(this::fromApiAccount).toList());
        return JsonSupport.encodeToString(res);
    }

    record Account(String accountId, int availableBalance, int postedBalance) {}

    record CreateAccountRequest(String accountId, int initialBalance) {}

    record GetAllAccountsResponse(java.util.List<Account> accounts) {}
    private Account fromApiAccount(AccountView.AccountSummary in) {
        return new Account(in.accountId(), in.availableBalance(), in.postedBalance());
    }

}
