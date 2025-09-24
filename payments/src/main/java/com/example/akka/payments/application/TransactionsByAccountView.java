package com.example.akka.payments.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.example.akka.payments.domain.TransactionState;

import java.util.Collection;

@ComponentId("transactions-by-account")
public class TransactionsByAccountView extends View {

    public record TransactionSummary(
            String idempotencyKey,
            String transactionId,
            String accountId,
            String authResult,
            String authStatus,
            String captureResult,
            String captureStatus,
            String cancelResult,
            String cancelStatus
    ) {}

    public record TransactionList(Collection<TransactionSummary> transactions) {}

    @Consume.FromWorkflow(TransactionWorkflow.class)
    public static class TransactionsByAccountUpdater extends TableUpdater<TransactionSummary> {

        public Effect<TransactionSummary> onUpdate(TransactionState transactionState) {
            var idempotencyKey = updateContext().eventSubject().orElse("");

            return effects().updateRow(
                    new TransactionSummary(
                            idempotencyKey,
                            transactionState.transactionId(),
                            transactionState.accountId(),
                            transactionState.authResult().name(),
                            transactionState.authStatus().name(),
                            transactionState.captureResult().name(),
                            transactionState.captureStatus().name(),
                            transactionState.cancelResult().name(),
                            transactionState.cancelStatus().name()
                    )
            );
        }
    }

    @Query("SELECT * AS transactions FROM transactions_by_account WHERE accountId = :accountId ORDER BY transactionId")
    public QueryEffect<TransactionList> getTransactionsByAccount(String accountId) {
        return queryResult();
    }
}