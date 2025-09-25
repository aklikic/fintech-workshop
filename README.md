# Demo

## Sequence diagram
```mermaid
sequenceDiagram
    actor US as  Upstream System
    participant Account as Account
    participant Card as Card
    participant Trans as Transaction
    actor DS as  Downstream System
    US->>Account: Create Account A1
    US->>Account: List accounts
    US->>Card: Create Card C1 and associate it with Account A1
    US->>Card: List cards
    opt Authorisation
        US->>Trans: Authorize amount X using Card C1 Transaction T1)
        Trans->>Card: Validate Card C1 and retrieve Account Id (A1)
        Trans->>Account: Authorize amount X using Account A1 for transaction T1
        Account->>Account: Validate against the current balance and authorise
        Account->>Trans: Authorised/Declined
        Trans-->DS: Notify transaction T1 authorised
    end
    opt Capture/Cancel
        US->>Trans: Capture/Cancel Transaction T1
        Trans->>Trans: Validate Transaction T1 state
        Trans->>Account: Capture/Cancel amount X using Account A1 for transaction T1
        Account->>Account: Validate against the current balance and previous made authorise
        Account->>Trans: Captured/Cancelled/Declined
        Trans-->DS: Notify transaction T1 captured/canceled
    end
    US->>Trans: List transactions

```

## Local run

Run corebanking service
```bash
 mvn exec:java -pl corebanking
```
Run payments service
```bash
 mvn exec:java -pl payments
```
Run backoffice service
```bash
 mvn exec:java -pl backoffice
```

## Local CLI test

Create Account
```bash
grpcurl -plaintext -d '{"account_id": "account-123", "initial_balance": 1000}' \
  localhost:9002 api.account.com.example.akka.backoffice.AccountGrpcEndpoint/CreateAccount
```

Create Card
```bash
grpcurl -plaintext -d '{"pan": "4111111111111111", "expiry_date": "12/25", "cvv": "123", "account_id": "account-123"}' \
  localhost:9001 api.payments.com.example.akka.backoffice.CardGrpcEndpoint/CreateCard
```

Authorize transaction
```bash
grpcurl -plaintext -d '{
  "idempotency_key": "unique-key-123", 
  "transaction_id": "txn-456", 
  "card_pan": "4111111111111111", 
  "card_expiry_date": "12/25", 
  "card_cvv": "123", 
  "amount": 500, 
  "currency": "USD"
}' \
  localhost:9001 api.payments.com.example.akka.backoffice.TransactionGrpcEndpoint/StartTransaction
```

Get transaction:
```bash
grpcurl -plaintext -d '{"idempotency_key": "unique-key-123"}' \
  localhost:9001 api.payments.com.example.akka.backoffice.TransactionGrpcEndpoint/GetTransaction
```

Capture Transaction:
```bash
grpcurl -plaintext -d '{"idempotency_key": "unique-key-123"}' \
  localhost:9001 api.payments.com.example.akka.backoffice.TransactionGrpcEndpoint/CaptureTransaction
```