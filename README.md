# Demo

## Local

Create Account
```bash
grpcurl -plaintext -d '{"account_id": "account-123", "initial_balance": 1000}' \
  localhost:9002 api.account.com.example.akka.ui.AccountGrpcEndpoint/CreateAccount
```

Create Card
```bash
grpcurl -plaintext -d '{"pan": "4111111111111111", "expiry_date": "12/25", "cvv": "123", "account_id": "account-123"}' \
  localhost:9001 api.payments.com.example.akka.ui.CardGrpcEndpoint/CreateCard
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
  localhost:9001 api.payments.com.example.akka.ui.TransactionGrpcEndpoint/StartTransaction
```

Get transaction:
```bash
grpcurl -plaintext -d '{"idempotency_key": "unique-key-123"}' \
  localhost:9001 api.payments.com.example.akka.ui.TransactionGrpcEndpoint/GetTransaction
```

Capture Transaction:
```bash
grpcurl -plaintext -d '{"idempotency_key": "unique-key-123"}' \
  localhost:9001 api.payments.com.example.akka.ui.TransactionGrpcEndpoint/CaptureTransaction
```