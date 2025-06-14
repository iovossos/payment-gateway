# Payment Gateway API Documentation

## Overview

The Payment Gateway API provides secure payment processing with fraud detection, user authentication, and comprehensive payment management. All endpoints return JSON responses following a consistent format.

**Base URL**: `http://localhost:8080/api`

## Response Format

All API responses follow this structure:

```json
{
  "success": boolean,
  "message": "string",
  "data": object | array | null,
  "timestamp": "ISO-8601 datetime",
  "error": "string" | null
}
```

---

## Authentication Endpoints

### POST /auth/login

Authenticate a user and receive a JWT token.

**Request:**
```
POST /api/auth/login
Content-Type: application/json
```

**Request Body:**
```json
{
  "username": "testuser",
  "password": "password123"
}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "username": "testuser",
    "issuedAt": "2025-06-14T03:46:08.457"
  },
  "timestamp": "2025-06-14T03:46:08.457",
  "error": null
}
```

**Error Response (401 Unauthorized):**
```json
{
  "success": false,
  "message": "Invalid username or password",
  "data": null,
  "timestamp": "2025-06-14T03:46:08.457",
  "error": "Invalid username or password"
}
```

### POST /auth/register

Register a new user account.

**Request Body:**
```json
{
  "username": "newuser",
  "email": "user@example.com",
  "password": "password123",
  "firstName": "John",
  "lastName": "Doe",
  "phoneNumber": "1234567890"
}
```

**Validation Rules:**
- `username`: 3-50 characters, required, unique
- `email`: Valid email format, required, unique
- `password`: Minimum 8 characters, required
- `firstName`: Maximum 50 characters, required
- `lastName`: Maximum 50 characters, required
- `phoneNumber`: Maximum 20 characters, optional

**Success Response (201 Created):**
```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "id": 1,
    "username": "newuser",
    "email": "user@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "phoneNumber": "1234567890",
    "active": true,
    "createdAt": "2025-06-14T03:46:08.457",
    "updatedAt": "2025-06-14T03:46:08.457"
  }
}
```

### GET /auth/me

Get information about the currently authenticated user.

**Request:**
```
GET /api/auth/me
Authorization: Bearer {jwt_token}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "username": "testuser",
    "email": "test@example.com",
    "firstName": "Test",
    "lastName": "User",
    "phoneNumber": "1234567890",
    "active": true,
    "createdAt": "2025-06-14T03:46:08.457",
    "updatedAt": "2025-06-14T03:46:08.457"
  }
}
```

### POST /auth/validate-token

Validate a JWT token.

**Request:**
```
POST /api/auth/validate-token?token={jwt_token}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Token is valid",
  "data": true
}
```

### POST /auth/logout

Logout the authenticated user.

**Request:**
```
POST /api/auth/logout
Authorization: Bearer {jwt_token}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Logged out successfully",
  "data": null
}
```

### POST /auth/refresh-token

Refresh an existing JWT token.

**Request:**
```
POST /api/auth/refresh-token
Authorization: Bearer {jwt_token}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Token refreshed successfully",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "username": "testuser",
    "issuedAt": "2025-06-14T03:46:08.457"
  }
}
```

---

## Payment Endpoints

### POST /payments

Process a new payment.

**Request:**
```
POST /api/payments
Authorization: Bearer {jwt_token}
Content-Type: application/json
```

**Request Body:**
```json
{
  "amount": 100.00,
  "currency": "USD",
  "paymentMethod": "CREDIT_CARD",
  "merchantReference": "ORDER-001",
  "description": "Product purchase",
  "cardDetails": {
    "cardNumber": "4111111111111111",
    "expiryMonth": "12",
    "expiryYear": "2025",
    "cvv": "123",
    "cardHolderName": "John Doe"
  }
}
```

**Payment Methods:**
- `CREDIT_CARD`
- `DEBIT_CARD`
- `BANK_TRANSFER`
- `DIGITAL_WALLET`
- `CRYPTOCURRENCY`

**Validation Rules:**
- `amount`: Minimum 0.01, maximum 15 digits with 2 decimal places
- `currency`: Exactly 3 characters (ISO 4217)
- `paymentMethod`: Required, maximum 50 characters
- `cardDetails`: Required for card payments

**Success Response (201 Created):**
```json
{
  "success": true,
  "message": "Payment processed successfully",
  "data": {
    "paymentId": 1,
    "amount": 100.00,
    "currency": "USD",
    "status": "COMPLETED",
    "paymentMethod": "CREDIT_CARD",
    "gatewayTransactionId": "TXN-ABC12345",
    "createdAt": "2025-06-14T03:46:09.357",
    "message": "Payment processed successfully"
  }
}
```

**Fraud Detection Response (403 Forbidden):**
```json
{
  "success": false,
  "message": "FRAUD DETECTED: Payment blocked due to high fraud risk (score: 1.00)",
  "data": null,
  "error": "FRAUD DETECTED: Payment blocked due to high fraud risk (score: 1.00)"
}
```

### GET /payments/{paymentId}

Get payment details by ID.

**Request:**
```
GET /api/payments/1
Authorization: Bearer {jwt_token}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "amount": 100.00,
    "currency": "USD",
    "status": "COMPLETED",
    "paymentMethod": "CREDIT_CARD",
    "merchantReference": "ORDER-001",
    "description": "Product purchase",
    "fraudScore": 0.25,
    "createdAt": "2025-06-14T03:46:09.330",
    "updatedAt": "2025-06-14T03:46:15.206",
    "userId": 1,
    "username": "testuser"
  }
}
```

### GET /payments/user/me

Get all payments for the authenticated user.

**Request:**
```
GET /api/payments/user/me
Authorization: Bearer {jwt_token}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "amount": 100.00,
      "currency": "USD",
      "status": "COMPLETED",
      "paymentMethod": "CREDIT_CARD",
      "merchantReference": "ORDER-001",
      "description": "Product purchase",
      "fraudScore": 0.25,
      "createdAt": "2025-06-14T03:46:09.330",
      "updatedAt": "2025-06-14T03:46:15.206",
      "userId": 1,
      "username": "testuser"
    }
  ]
}
```

### GET /payments/user/me/paginated

Get paginated payments for the authenticated user.

**Request:**
```
GET /api/payments/user/me/paginated?page=0&size=10&sort=createdAt,desc
Authorization: Bearer {jwt_token}
```

**Query Parameters:**
- `page`: Page number (default: 0)
- `size`: Page size (default: 20)
- `sort`: Sort criteria (e.g., "createdAt,desc")

**Success Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "amount": 100.00,
        "currency": "USD",
        "status": "COMPLETED",
        "paymentMethod": "CREDIT_CARD",
        "fraudScore": 0.25,
        "createdAt": "2025-06-14T03:46:09.330",
        "userId": 1,
        "username": "testuser"
      }
    ],
    "pageable": {
      "sort": {
        "sorted": true,
        "unsorted": false
      },
      "pageNumber": 0,
      "pageSize": 10
    },
    "totalElements": 1,
    "totalPages": 1,
    "last": true,
    "first": true,
    "numberOfElements": 1
  }
}
```

### POST /payments/{paymentId}/refund

Process a refund for a payment.

**Request:**
```
POST /api/payments/1/refund
Authorization: Bearer {jwt_token}
Content-Type: application/json
```

**Request Body:**
```json
{
  "amount": 50.00,
  "reason": "Customer request"
}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Refund processed successfully",
  "data": {
    "paymentId": 1,
    "amount": 50.00,
    "currency": "USD",
    "status": "PARTIALLY_REFUNDED",
    "gatewayTransactionId": "RFD-XYZ67890",
    "message": "Refund processed successfully",
    "createdAt": "2025-06-14T03:46:15.208"
  }
}
```

### POST /payments/{paymentId}/cancel

Cancel a pending payment.

**Request:**
```
POST /api/payments/1/cancel
Authorization: Bearer {jwt_token}
Content-Type: application/json
```

**Request Body:**
```json
{
  "reason": "Customer cancellation"
}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Payment cancelled successfully",
  "data": null
}
```

### GET /payments/status/{status}

Get all payments by status.

**Request:**
```
GET /api/payments/status/COMPLETED
Authorization: Bearer {jwt_token}
```

**Payment Statuses:**
- `PENDING`
- `PROCESSING`
- `COMPLETED`
- `FAILED`
- `CANCELLED`
- `REFUNDED`
- `PARTIALLY_REFUNDED`

**Success Response (200 OK):**
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "amount": 100.00,
      "status": "COMPLETED",
      "paymentMethod": "CREDIT_CARD",
      "fraudScore": 0.25,
      "createdAt": "2025-06-14T03:46:09.330",
      "userId": 1,
      "username": "testuser"
    }
  ]
}
```

---

## Analytics Endpoints

### GET /payments/analytics/total

Get total payment amount by status for the authenticated user.

**Request:**
```
GET /api/payments/analytics/total?status=COMPLETED
Authorization: Bearer {jwt_token}
```

**Query Parameters:**
- `status`: Payment status (default: COMPLETED)

**Success Response (200 OK):**
```json
{
  "success": true,
  "data": 1250.00
}
```

### GET /payments/analytics/date-range

Get total successful payments between dates.

**Request:**
```
GET /api/payments/analytics/date-range?startDate=2025-06-01T00:00:00&endDate=2025-06-30T23:59:59
```

**Query Parameters:**
- `startDate`: Start date (ISO-8601 format)
- `endDate`: End date (ISO-8601 format)

**Success Response (200 OK):**
```json
{
  "success": true,
  "data": 2500.00
}
```

### GET /payments/high-risk

Get high-risk payments above fraud threshold.

**Request:**
```
GET /api/payments/high-risk?threshold=0.3
```

**Query Parameters:**
- `threshold`: Fraud score threshold (default: 0.5)

**Success Response (200 OK):**
```json
{
  "success": true,
  "data": [
    {
      "id": 2,
      "amount": 5000.00,
      "fraudScore": 0.45,
      "status": "COMPLETED",
      "paymentMethod": "CRYPTOCURRENCY",
      "createdAt": "2025-06-14T03:46:09.330",
      "userId": 1,
      "username": "testuser"
    }
  ]
}
```

---

## Health Endpoints

### GET /health

Check API health status with detailed information.

**Request:**
```
GET /api/health
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "Service is healthy",
  "data": {
    "status": "UP",
    "timestamp": "2025-06-14T03:46:09.211",
    "service": "payment-gateway",
    "version": "1.0.0"
  }
}
```

### GET /health/check

Simple health check endpoint.

**Request:**
```
GET /api/health/check
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "OK"
}
```

---

## Error Codes

| HTTP Status | Description | Common Causes |
|-------------|-------------|---------------|
| 400 | Bad Request | Invalid input data, validation errors, missing required fields |
| 401 | Unauthorized | Missing or invalid JWT token, invalid credentials |
| 403 | Forbidden | Fraud detected, insufficient permissions |
| 404 | Not Found | Payment, user, or resource not found |
| 409 | Conflict | Username or email already exists, duplicate merchant reference |
| 500 | Internal Server Error | Database errors, unexpected system errors |

## Fraud Detection

### Fraud Scoring Algorithm

The system calculates fraud scores based on multiple factors:

**Amount Risk (0.0 - 0.5):**
- $15,000+: 0.5 risk
- $5,000+: 0.3 risk
- $1,000+: 0.1 risk

**Payment Method Risk (0.0 - 0.2):**
- Cryptocurrency: 0.2 risk
- Digital Wallet: 0.08 risk
- Credit Card: 0.05 risk
- Debit Card: 0.02 risk
- Bank Transfer: 0.0 risk

**Frequency Risk (0.0 - 0.4):**
- 3+ payments/hour: 0.4 risk
- 10+ payments/day: 0.3 risk
- 2+ payments/hour: 0.2 risk
- 5+ payments/day: 0.1 risk

**User Behavior Risk (0.0 - 0.3):**
- New user: 0.2 risk
- Payment 10x larger than average: 0.3 risk
- Payment 5x larger than average: 0.2 risk
- Payment 3x larger than average: 0.1 risk
- Multiple failed payments: 0.1 risk

### Risk Thresholds

- **Low Risk**: < 0.2 (Allow)
- **Medium Risk**: 0.2 - 0.5 (Allow with monitoring)
- **High Risk**: > 0.5 (Block payment)

## Authentication

### JWT Token Usage

Include JWT token in the Authorization header for all protected endpoints:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0dXNlciIsImlhdCI6MTc0OTg2MTk2OCwiZXhwIjoxNzQ5ODY1NTY4fQ.YuM56r_jzl03f2pYTYHwmTsuRDcj0YgWL-YgOKD8DQk
```

### Token Details

- **Algorithm**: HMAC SHA256
- **Contains**: Username, issued time, expiration time

## Test Data

### Default Users

The application creates test users on startup:

```json
{
  "username": "testuser",
  "password": "password123",
  "email": "test@example.com"
}
```

```json
{
  "username": "admin",
  "password": "admin123",
  "email": "admin@example.com"
}
```

## Development Configuration

**Database**: H2 in-memory database for development
**Console**: Available at `/h2-console`
**JWT Settings**: Configurable in `application.properties`

```properties
app.jwt.secret=your-secret-key
app.jwt.expiration=3600
```