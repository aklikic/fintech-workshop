# Core Banking Service

This service implements account management functionality using Akka SDK.

## gRPC Endpoints

The corebanking service exposes one gRPC endpoint:

### AccountGrpcEndpoint gRPC Commands

Use the following grpcurl commands to interact with the AccountGrpcEndpoint service:

#### Local Development
```bash
# Create Account
grpcurl -plaintext -d '{"account_id": "account-123", "initial_balance": 1000}' \
  localhost:9002 com.example.akka.account.api.AccountGrpcEndpoint/CreateAccount
```


# Get Account
grpcurl -plaintext -d '{"account_id": "account-123"}' \
  localhost:9002 com.example.akka.account.api.AccountGrpcEndpoint/GetAccount

# Authorize Transaction
grpcurl -plaintext -d '{"account_id": "account-123", "transaction_id": "txn-456", "amount": 500}' \
  localhost:9002 com.example.akka.account.api.AccountGrpcEndpoint/AuthorizeTransaction

# Capture Transaction
grpcurl -plaintext -d '{"account_id": "account-123", "transaction_id": "txn-456"}' \
  localhost:9002 com.example.akka.account.api.AccountGrpcEndpoint/CaptureTransaction

# Get Expenditure
grpcurl -plaintext -d '{"account_id": "account-123"}' \
  localhost:9002 com.example.akka.account.api.AccountGrpcEndpoint/GetExpenditure
```

#### Cloud Deployment
```bash
# Create Account
grpcurl -d '{"account_id": "account-1234", "initial_balance": 1000}' \
  small-cloud-1731.gcp-us-east1.akka.services:443 com.example.akka.account.api.AccountGrpcEndpoint/CreateAccount
```
```bash
# Get Account
grpcurl -d '{"account_id": "account-1234"}' \
  small-cloud-1731.gcp-us-east1.akka.services:443 com.example.akka.account.api.AccountGrpcEndpoint/GetAccount
```

# Authorize Transaction
grpcurl -d '{"account_id": "account-123", "transaction_id": "txn-456", "amount": 500}' \
  small-cloud-1731.gcp-us-east1.akka.services:443 com.example.akka.account.api.AccountGrpcEndpoint/AuthorizeTransaction

# Capture Transaction
grpcurl -d '{"account_id": "account-123", "transaction_id": "txn-456"}' \
  small-cloud-1731.gcp-us-east1.akka.services:443 com.example.akka.account.api.AccountGrpcEndpoint/CaptureTransaction

# Get Expenditure
grpcurl -d '{"account_id": "account-123"}' \
  small-cloud-1731.gcp-us-east1.akka.services:443 com.example.akka.account.api.AccountGrpcEndpoint/GetExpenditure
```

## Components

### Event Sourced Entities
- **AccountEntity**: Manages account data and transaction authorizations with account ID as entity ID

### gRPC Endpoints
- **AccountGrpcEndpointImpl**: Provides account management, transaction authorization, and expenditure tracking

## Account Processing Flow

1. **Create Account**: Client calls `CreateAccount` with account ID and initial balance
2. **Authorize Transaction**: Payments service calls `AuthorizeTransaction` to reserve funds
3. **Capture Transaction**: Payments service calls `CaptureTransaction` to complete the transaction
4. **Get Expenditure**: Client can query account spending with `GetExpenditure`

**Note:** This service runs on port 9002 as configured in application.conf