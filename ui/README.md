# UI Service API Gateway - CURL Commands

## Account Operations

Create Account:
```bash
curl -X POST http://localhost:9003/api/accounts -H "Content-Type: application/json" -d '{"accountId": "account-123", "initialBalance": 1000}'
```

Get Account:
```bash
curl -X GET http://localhost:9003/api/accounts/account-123
```

Get All Accounts:
```bash
curl -X GET http://localhost:9003/api/accounts
```

Authorize Transaction:
```bash
curl -X POST http://localhost:9003/api/accounts/account-123/authorize -H "Content-Type: application/json" -d '{"transactionId": "txn-456", "amount": 500}'
```

Capture Transaction:
```bash
curl -X POST http://localhost:9003/api/accounts/account-123/capture -H "Content-Type: application/json" -d '{"transactionId": "txn-456"}'
```

Get Account Expenditure:
```bash
curl -X GET http://localhost:9003/api/accounts/account-123/expenditure
```

## Card Operations

Create Card:
```bash
curl -X POST http://localhost:9003/api/cards -H "Content-Type: application/json" -d '{"pan": "4111111111111111", "expiryDate": "12/25", "cvv": "123", "accountId": "account-123"}'
```

Get Card:
```bash
curl -X GET http://localhost:9003/api/cards/4111111111111111
```

Validate Card:
```bash
curl -X POST http://localhost:9003/api/cards/validate -H "Content-Type: application/json" -d '{"pan": "4111111111111111", "expiryDate": "12/25", "cvv": "123"}'
```

## Transaction Operations

Start Transaction:
```bash
curl -X POST http://localhost:9003/api/transactions/start -H "Content-Type: application/json" -d '{"idempotencyKey": "unique-key-123", "transactionId": "txn-456", "cardPan": "4111111111111111", "cardExpiryDate": "12/25", "cardCvv": "123", "amount": 500, "currency": "USD"}'
```

Get Transaction:
```bash
curl -X GET http://localhost:9003/api/transactions/unique-key-123
```

Capture Transaction:
```bash
curl -X POST http://localhost:9003/api/transactions/unique-key-123/capture
```

Get Transactions by Account:
```bash
curl -X GET http://localhost:9003/api/accounts/account-123/transactions
```