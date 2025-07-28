package com.example.akka.corebanking.application;


import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.example.akka.corebanking.domain.AccountEvent;
import com.example.akka.corebanking.domain.TotalExpenditure;

@ComponentId("total-expenditure-view")
public class TotalExpenditureView extends View {


    @Consume.FromEventSourcedEntity(AccountEntity.class)
    public static class Updater extends TableUpdater<TotalExpenditure> {

        public Effect<TotalExpenditure> onUpdate(AccountEvent event) {
            return switch (event) {
                case AccountEvent.Created create ->
                        effects().updateRow(new TotalExpenditure(create.accountId(), create.initialBalance(), 0));
                case AccountEvent.TransAuthorisationAdded auth ->
                        effects().updateRow(new TotalExpenditure(rowState().accountId(), rowState().moneyIn(), rowState().moneyOut() + auth.amount()));
                case AccountEvent.TransCaptureAdded ignored -> effects().ignore();
            };
        }
    }

    @Query("SELECT * FROM total_expenditure_view WHERE accountId = :accountId")
    public QueryEffect<TotalExpenditure> get(String accountId) {
        return queryResult();
    }

}
