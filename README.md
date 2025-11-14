# Demo

## Sequence diagram
```mermaid
sequenceDiagram
    participant BO as BackOffice
    participant Account as Account
    participant Card as Card
    participant Trans as Transaction
    opt Administration
        BO->>Account: Create Account A1
        BO->>Account: List accounts
        BO->>Trans: List transactions
        BO->>Card: Create Card C1 and associate it with Account A1
        BO->>Card: List cards
    end
    opt Authorisation
        BO->>Trans: Authorize amount X using Card C1 Transaction T1)
        Trans->>Card: Validate Card C1 and retrieve Account Id (A1)
        Trans->>Account: Authorize amount X using Account A1 for transaction T1
        Account->>Account: Validate against the current balance and authorise
        Account->>Trans: Authorised/Declined
    end
    opt Capture/Cancel
        BO->>Trans: Capture/Cancel Transaction T1
        Trans->>Trans: Validate Transaction T1 state
        Trans->>Account: Capture/Cancel amount X using Account A1 for transaction T1
        Account->>Account: Validate against the current balance and previous made authorise
        Account->>Trans: Captured/Cancelled/Declined
    end
```
## Sequence diagram with AI
```mermaid
---
config:
theme: redux-color
look: handDrawn
---
sequenceDiagram
participant Agent as BackOffice AI Assistent Agent
participant Account as Account
participant Card as Card
participant Trans as Transaction
Agent->>Account: Create Account A1
Agent->>Account: List accounts
Agent->>Trans: List transactions
Agent->>Card: Create Card C1 and associate it with Account A1
Agent->>Card: List cards
Agent->>Trans: Authorize amount X using Card C1 Transaction T1)
Agent->>Trans: Capture/Cancel Transaction T1
```
## Local run

Compile and publish locally the API project
```bash
mvn compile install -pl api
```

Run corebanking service
```bash
 mvn compile exec:java -pl corebanking
```
Run payments service
```bash
 mvn compile exec:java -pl payments
```
Run backoffice service
```bash
 mvn compile exec:java -pl backoffice
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