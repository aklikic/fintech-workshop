package com.example.akka.payments.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.example.akka.payments.domain.AccountToCard;

@ComponentId("account-to-card-entity")
public class AccountToCardEntity extends KeyValueEntity<AccountToCard> {
  
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
  
  public Effect<AccountToCard> getAccountToCard() {
    return effects().reply(currentState());
  }
  
  public record CreateCardCommand(String pan, String accountId) {
  }
}
