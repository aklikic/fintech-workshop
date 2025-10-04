package com.example.akka.payments.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import com.example.akka.payments.domain.CardEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("card-view")
public class CardView extends View {

    private static final Logger logger = LoggerFactory.getLogger(CardView.class);

    public record CardSummary(String pan, String expiryDate, String accountId) {}

    public record CardList(java.util.List<CardSummary> cards) {}

    @Consume.FromEventSourcedEntity(value = CardEntity.class)
    public static class CardViewUpdater extends TableUpdater<CardSummary> {

        public Effect<CardSummary> onUpdate(CardEvent event) {
            logger.info("Received card event {}", event);
            return switch (event) {
                case CardEvent.Created create ->
                        effects().updateRow(new CardSummary(
                                create.pan(),
                                create.expiryDate(),
                                create.accountId()));
            };
        }
    }

    @Query("SELECT * AS cards FROM card_view")
    public QueryEffect<CardList> getAllCards() {
        return queryResult();
    }

    @Query("SELECT * AS cards FROM card_view WHERE accountId = :accountId")
    public QueryEffect<CardList> getCardsByAccount(String accountId) {
        return queryResult();
    }

    @Query("SELECT * FROM card_view WHERE pan = :pan")
    public QueryEffect<CardSummary> getCardByPan(String pan) {
        return queryResult();
    }
}