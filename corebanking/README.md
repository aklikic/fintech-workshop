# Core Banking Service

This service implements account management functionality using Akka SDK.

## AccountGrpcEndpoint gRPC Commands

Use the following grpcurl commands to interact with the AccountGrpcEndpoint service:

### Create Account
```bash
grpcurl -plaintext -d '{"account_id": "account-123", "initial_balance": 1000}' \
  localhost:9002 com.example.akka.account.api.AccountGrpcEndpoint/CreateAccount
```

### Get Account
```bash
grpcurl -plaintext -d '{"account_id": "account-123"}' \
  localhost:9002 com.example.akka.account.api.AccountGrpcEndpoint/GetAccount
```

### Authorize Transaction
```bash
grpcurl -plaintext -d '{"account_id": "account-123", "transaction_id": "tx-123", "amount": 500}' \
  localhost:9002 com.example.akka.account.api.AccountGrpcEndpoint/AuthorizeTransaction
```

### Capture Transaction
```bash
grpcurl -plaintext -d '{"account_id": "account-123", "transaction_id": "tx-456"}' \
  localhost:9002 com.example.akka.account.api.AccountGrpcEndpoint/CaptureTransaction
```

### Get Expenditure
```bash
grpcurl -plaintext -d '{"account_id": "account-123"}' \
  localhost:9002 com.example.akka.account.api.AccountGrpcEndpoint/GetExpenditure
```

**Note:** This service runs on port 9002 as configured in application.conf