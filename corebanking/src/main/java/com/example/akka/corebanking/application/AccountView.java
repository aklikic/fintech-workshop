package com.example.akka.corebanking.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.example.akka.corebanking.domain.AccountEvent;
import org.slf4j.Logger;

@Component(id = "account-view")
public class AccountView extends View {

    private final static Logger logger = org.slf4j.LoggerFactory.getLogger(AccountView.class);
    public record AccountSummary(String accountId, int availableBalance, int postedBalance) {}

    public record AccountList(java.util.List<AccountSummary> accounts) {}

    @Consume.FromEventSourcedEntity(value = AccountEntity.class)
    public static class AccountViewUpdater extends TableUpdater<AccountSummary> {
        public Effect<AccountSummary> onUpdate(AccountEvent event) {
            logger.info("Received event {}", event);
            return switch (event) {
                case AccountEvent.Created create ->
                        effects().updateRow(new AccountSummary(create.accountId(), create.initialBalance(), create.initialBalance()));
                case AccountEvent.TransAuthorisationAdded auth -> {
                    var current = rowState();
                    if (current == null) yield effects().ignore();
                    yield effects().updateRow(new AccountSummary(
                            current.accountId(),
                            current.availableBalance() - auth.amount(),
                            current.postedBalance()));
                }
                case AccountEvent.TransCaptureAdded capture -> {
                    var current = rowState();
                    if (current == null) yield effects().ignore();
                    yield effects().updateRow(new AccountSummary(
                            current.accountId(),
                            current.availableBalance(),
                            current.postedBalance() - capture.amount()));
                }
                case AccountEvent.TransCancelAdded cancel -> {
                    var current = rowState();
                    if (current == null) yield effects().ignore();
                    yield effects().updateRow(new AccountSummary(
                            current.accountId(),
                            current.availableBalance() + cancel.amount(),
                            current.postedBalance()));
                }
            };
        }
    }

    @Query("SELECT * AS accounts FROM account_view")
    public QueryEffect<AccountList> getAllAccounts() {
        return queryResult();
    }
}