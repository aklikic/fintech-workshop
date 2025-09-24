package com.example.akka.payments.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.akka.payments.domain.AccountToCard;

import java.util.Optional;

@ComponentId("account-to-card-mapping-entity")
public class CardByAccountEntity extends KeyValueEntity<AccountToCard> {
  
  @Override
  public AccountToCard emptyState() {
    return new AccountToCard("", "", false);
  }
  
  public Effect<Done> createCard(CreateCardCommand cmd) {
    return effects()
        .updateState(new AccountToCard(cmd.accountId, cmd.pan(), false))
        .thenReply(Done.getInstance());
  }
  
  public Effect<Done> activateCard() {
    return effects()
        .updateState(currentState().activate())
        .thenReply(Done.getInstance());
  }
  
  public ReadOnlyEffect<Optional<AccountToCard>> getAccountToCard() {
      if(currentState().accountId().isEmpty())
          return effects().reply(Optional.empty());
      return effects().reply(Optional.of(currentState()));
  }
  
  public record CreateCardCommand(String pan, String accountId) {
  }
}
