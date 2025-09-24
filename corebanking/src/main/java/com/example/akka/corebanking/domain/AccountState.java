package com.example.akka.corebanking.domain;

import java.util.List;
import java.util.Optional;

public record AccountState(String accountId, List<Authorisation> authorisations, int availableBalance, int postedBalance) {
    public static AccountState empty() {
        return new AccountState("", List.of(), 0, 0);
    }

    public boolean isEmpty() {
        return accountId.isEmpty();
    }

    public boolean isAvailableBalance(int amount) {
        return availableBalance >= amount;
    }

    public Optional<Authorisation> getAuthorisation(String transactionId) {
        return authorisations.stream()
                .filter(a -> a.transactionId().equals(transactionId))
                .findFirst();
    }

    public AccountState onCreate(AccountEvent.Created event) {
        return new AccountState(event.accountId(), List.of(), event.initialBalance(), event.initialBalance());
    }

    public AccountState onAuthorisationAdded(AccountEvent.TransAuthorisationAdded event) {
        var newAuth = new Authorisation(event.transactionId(), event.amount(), event.authCode());
        var newAuths = new java.util.ArrayList<>(authorisations);
        newAuths.add(newAuth);
        return new AccountState(accountId, newAuths, availableBalance - event.amount(), postedBalance);
    }

    public AccountState onCaptureAdded(AccountEvent.TransCaptureAdded event) {
        
            var auth = authorisations().stream().filter(a -> a.transactionId().equals(event.transactionId())).findFirst().get();
            var newAuths = authorisations.stream()
                    .filter(a -> !a.transactionId().equals(event.transactionId()))
                    .toList();
            return new AccountState(accountId, newAuths, availableBalance, postedBalance - auth.amount());
        
    }

    public AccountState onCancelAdded(AccountEvent.TransCancelAdded event) {

        var auth = authorisations().stream().filter(a -> a.transactionId().equals(event.transactionId())).findFirst().get();
        var newAuths = authorisations.stream()
                .filter(a -> !a.transactionId().equals(event.transactionId()))
                .toList();
        return new AccountState(accountId, newAuths, availableBalance + auth.amount(), postedBalance );

    }
    
    public record Authorisation(String transactionId, int amount, String authCode) {}
}