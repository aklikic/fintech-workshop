# Payments Service

This service implements card management and validation functionality using Akka SDK.

## CardGrpcEndpoint gRPC Commands

Use the following grpcurl commands to interact with the CardGrpcEndpoint service:

### Create Card
```bash
grpcurl -plaintext -d '{"pan": "4111111111111111", "expiry_date": "12/25", "cvv": "123", "account_id": "account-123"}' \
  localhost:9001 com.example.akka.payments.api.CardGrpcEndpoint/CreateCard
```

### Get Card
```bash
grpcurl -plaintext -d '{"pan": "4111111111111111"}' \
  localhost:9001 com.example.akka.payments.api.CardGrpcEndpoint/GetCard
```

### Validate Card
```bash
grpcurl -plaintext -d '{"pan": "4111111111111111", "expiry_date": "12/25", "cvv": "123"}' \
  localhost:9001 com.example.akka.payments.api.CardGrpcEndpoint/ValidateCard
```

**Note:** This service runs on port 9001 as configured in application.conf