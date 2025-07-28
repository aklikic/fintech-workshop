package com.example.akka.corebanking.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import com.example.akka.corebanking.domain.CardEvent;
import com.example.akka.corebanking.domain.CardState;

@ComponentId("card-entity")
public class CardEntity extends EventSourcedEntity<CardState, CardEvent> {

    @Override
    public CardState emptyState() {
        return CardState.empty();
    }


    public Effect<ApiCard> createCard(ApiCard in) {
        if(currentState().isEmpty()){
            CardEvent.Created event = new CardEvent.Created(
                    in.pan(),
                    in.expiryDate(),
                    in.cvv(),
                    in.accountId()
            );
            return effects().persist(event).thenReply(this::fromState);
        }else{
            return effects().reply(fromState(currentState()));
        }

    }

    public Effect<ApiCard> getCard() {
        if (currentState().isEmpty()) {
            return effects().error("Card not found");
        } else {
            return effects().reply(fromState(currentState()));
        }
    }

    private ApiCard fromState(CardState state) {
        return new ApiCard(
                state.pan(),
                state.expiryDate(),
                state.cvv(),
                state.accountId()
        );
    }

    @Override
    public CardState applyEvent(CardEvent cardEvent) {
        return switch (cardEvent) {
            case CardEvent.Created created -> currentState().onCreate(created);
            default -> currentState();
        };
    }

    public record ApiCard(String pan, String expiryDate, String cvv, String accountId) {

        public static ApiCard empty() {
            return new ApiCard("", "", "", "");
        }

        public boolean isEmpty() {
            return pan == null || pan.isEmpty();
        }
    }
}
