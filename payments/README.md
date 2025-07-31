# Payments Service

This service implements card management, transaction processing, and workflow orchestration using Akka SDK.

## gRPC Endpoints

The payments service exposes two gRPC endpoints:

### CardGrpcEndpoint gRPC Commands

Use the following grpcurl commands to interact with the CardGrpcEndpoint service:

#### Local Development
```bash
# Create Card
grpcurl -plaintext -d '{"pan": "4111111111111111", "expiry_date": "12/25", "cvv": "123", "account_id": "account-123"}' \
  localhost:9001 com.example.akka.payments.api.CardGrpcEndpoint/CreateCard
```
```bash
# Get Card
grpcurl -plaintext -d '{"pan": "4111111111111111"}' \
  localhost:9001 com.example.akka.payments.api.CardGrpcEndpoint/GetCard

# Validate Card
grpcurl -plaintext -d '{"pan": "4111111111111111", "expiry_date": "12/25", "cvv": "123"}' \
  localhost:9001 com.example.akka.payments.api.CardGrpcEndpoint/ValidateCard
```

#### Cloud Deployment
```bash
# Create Card
grpcurl -d '{"pan": "4111111111111111", "expiry_date": "12/25", "cvv": "123", "account_id": "account-123"}' \
  icy-salad-1140.gcp-us-east1.akka.services:443 com.example.akka.payments.api.CardGrpcEndpoint/CreateCard
```


# Get Card
grpcurl -d '{"pan": "4111111111111111"}' \
  icy-salad-1140.gcp-us-east1.akka.services:443 com.example.akka.payments.api.CardGrpcEndpoint/GetCard

# Validate Card
grpcurl -d '{"pan": "4111111111111111", "expiry_date": "12/25", "cvv": "123"}' \
  icy-salad-1140.gcp-us-east1.akka.services:443 com.example.akka.payments.api.CardGrpcEndpoint/ValidateCard
```

### TransactionGrpcEndpoint gRPC Commands

Use the following grpcurl commands to interact with the TransactionGrpcEndpoint service:

#### Local Development
```bash
# Start Transaction
grpcurl -plaintext -d '{
  "idempotency_key": "unique-key-123", 
  "transaction_id": "txn-456", 
  "card_pan": "4111111111111111", 
  "card_expiry_date": "12/25", 
  "card_cvv": "123", 
  "amount": 500, 
  "currency": "USD"
}' \
  localhost:9001 com.example.akka.payments.api.TransactionGrpcEndpoint/StartTransaction

# Get Transaction
grpcurl -plaintext -d '{"idempotency_key": "unique-key-123"}' \
  localhost:9001 com.example.akka.payments.api.TransactionGrpcEndpoint/GetTransaction

# Capture Transaction
grpcurl -plaintext -d '{"idempotency_key": "unique-key-123"}' \
  localhost:9001 com.example.akka.payments.api.TransactionGrpcEndpoint/CaptureTransaction
```

#### Cloud Deployment
```bash
# Start Transaction
grpcurl -d '{
  "idempotency_key": "unique-key-123", 
  "transaction_id": "txn-456", 
  "card_pan": "4111111111111111", 
  "card_expiry_date": "12/25", 
  "card_cvv": "123", 
  "amount": 500, 
  "currency": "USD"
}' \
  icy-salad-1140.gcp-us-east1.akka.services:443 com.example.akka.payments.api.TransactionGrpcEndpoint/StartTransaction
```
```bash
# Get Transaction
grpcurl -d '{"idempotency_key": "unique-key-123"}' \
  icy-salad-1140.gcp-us-east1.akka.services:443 com.example.akka.payments.api.TransactionGrpcEndpoint/GetTransaction
  ```
```bash
# Capture Transaction
grpcurl -d '{"idempotency_key": "unique-key-123"}' \
  icy-salad-1140.gcp-us-east1.akka.services:443 com.example.akka.payments.api.TransactionGrpcEndpoint/CaptureTransaction
```

## Components

### Event Sourced Entities
- **CardEntity**: Manages card data with card PAN as entity ID

### Workflows
- **TransactionWorkflow**: Orchestrates transaction processing with the following steps:
  1. **validate-card**: Validates card details against stored card data
  2. **authorize-transaction**: Calls the corebanking service to authorize the transaction and pauses
  3. **capture-transaction**: Captures the authorized transaction (triggered externally)
  
  The workflow uses the idempotency key as the workflow ID and includes a 5-minute timeout.

### gRPC Endpoints
- **CardGrpcEndpointImpl**: Provides CRUD operations for card management
- **TransactionGrpcEndpointImpl**: Exposes the TransactionWorkflow's `startTransaction` and `captureTransaction` methods and provides transaction status queries

## Transaction Processing Flow

1. **Start Transaction**: Client calls `StartTransaction` with card details and transaction information
2. **Card Validation**: Workflow validates the provided card details against stored card data
3. **Account Authorization**: If card is valid, workflow calls the corebanking service to authorize the transaction and pauses
4. **Capture Transaction**: Client calls `CaptureTransaction` to complete the transaction capture
5. **Response**: Client receives transaction status and can query for updates using `GetTransaction`

## Service Configuration

**Note:** This service runs on port 9001 as configured in application.conf