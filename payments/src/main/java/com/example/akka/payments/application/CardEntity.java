package com.example.akka.payments.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import com.example.akka.payments.domain.CardEvent;
import com.example.akka.payments.domain.CardState;

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

    public ReadOnlyEffect<ApiCard> getCard() {
        if (currentState().isEmpty()) {
            return effects().error("Card not found");
        } else {
            return effects().reply(fromState(currentState()));
        }
    }

    public Effect<ApiCard> activate() {
        if (currentState().isEmpty()) {
            return effects().error("Card not found");
        } else if (currentState().active()) {
            return effects().reply(fromState(currentState())); // already active
        } else {
            CardState state = currentState();
            return effects()
                .persist(new CardEvent.Activated(state.pan(), state.accountId()))
                .thenReply(this::fromState);
        }
    }

    private ApiCard fromState(CardState state) {
        return new ApiCard(
                state.pan(),
                state.expiryDate(),
                state.cvv(),
                state.accountId(),
                state.active()
        );
    }

    @Override
    public CardState applyEvent(CardEvent cardEvent) {
        return switch (cardEvent) {
            case CardEvent.Created created -> currentState().onCreate(created);
            case CardEvent.Activated activated -> currentState().onActivated();
            default -> currentState();
        };
    }

    public record ApiCard(String pan, String expiryDate, String cvv, String accountId, boolean active) {

        public static ApiCard empty() {
            return new ApiCard("", "", "", "", false);
        }

        public boolean isEmpty() {
            return pan == null || pan.isEmpty();
        }
    }
}
