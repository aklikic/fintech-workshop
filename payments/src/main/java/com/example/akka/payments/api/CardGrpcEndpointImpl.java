package com.example.akka.payments.api;

import akka.grpc.GrpcServiceException;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.GrpcEndpoint;
import akka.javasdk.client.ComponentClient;
import com.example.akka.payments.application.CardEntity;
import io.grpc.Status;
import org.slf4j.Logger;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@GrpcEndpoint
public class CardGrpcEndpointImpl implements CardGrpcEndpoint {

    private final static Logger logger = org.slf4j.LoggerFactory.getLogger(CardGrpcEndpointImpl.class);
    private final ComponentClient componentClient;

    public CardGrpcEndpointImpl(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    @Override
    public Card createCard(Card in) {
        logger.info("Creating card {} for account id {}", in.getPan(), in.getAccountId());
        try {
            var res = componentClient.forEventSourcedEntity(in.getPan())
                .method(CardEntity::createCard)
                .invoke(new CardEntity.ApiCard(in.getPan(), in.getExpiryDate(), in.getCvv(), in.getAccountId()));
            return fromState(res);
        }catch (Exception e){
            throw new GrpcServiceException(Status.INTERNAL.augmentDescription(e.getMessage()));
        }
    }

    @Override
    public ValidateCardResponse validateCard(ValidateCardRequest in) {
        try {
            var card = componentClient.forEventSourcedEntity(in.getPan()).method(CardEntity::getCard).invoke();
            if (card.isEmpty()) {
                throw new GrpcServiceException(Status.NOT_FOUND);
            }
            var valid = card.expiryDate().equals(in.getExpiryDate()) && card.cvv().equals(in.getCvv());
            return ValidateCardResponse.newBuilder().setIsValid(valid).build();
        }catch (Exception e){
            throw new GrpcServiceException(Status.INTERNAL.augmentDescription(e.getMessage()));
        }
    }

    @Override
    public Card getCard(GetCardRequest in) {
        try {
            var card = componentClient.forEventSourcedEntity(in.getPan()).method(CardEntity::getCard).invoke();
            if (card.isEmpty()) {
                throw new GrpcServiceException(Status.NOT_FOUND);
            }
            return fromState(card);
        }catch (Exception e){
            throw new GrpcServiceException(Status.INTERNAL.augmentDescription(e.getMessage()));
        }
    }

    private Card fromState(CardEntity.ApiCard card) {
        return Card.newBuilder()
                .setPan(card.pan())
                .setExpiryDate(card.expiryDate())
                .setCvv(card.cvv())
                .setAccountId(card.accountId())
                .build();
    }
}
