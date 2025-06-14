# Payment Gateway

A secure payment processing system built with Java 21 and Spring Boot, featuring JWT authentication, fraud detection, and comprehensive payment management capabilities.
(Running ./mvnw clean install gives 1 test failure, but running with ./mvnw spring-boot:run works and allows you to test the api thoroughly)
## Features

### Core Functionality
- Payment processing for multiple payment methods (credit card, debit card, bank transfer, digital wallet, cryptocurrency)
- User registration and JWT-based authentication
- Payment refund and cancellation management
- Transaction history and analytics
- Real-time fraud detection with configurable scoring

### Security Features
- JWT token authentication with configurable expiration
- BCrypt password encryption
- Input validation and sanitization
- Fraud detection algorithm with risk scoring
- Secure payment data handling

### Technical Features
- RESTful API design with consistent response format
- H2 database with JPA/Hibernate ORM
- Comprehensive error handling and logging
- Paginated responses for large datasets
- Audit trails with automatic timestamping

## Getting Started

### Clone the Repository

```bash
git clone https://github.com/yourusername/payment-gateway.git
cd payment-gateway
```

### Build the Project

```bash
mvn clean install
```

### Run the Application

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

### Access Points

- **API Base URL**: `http://localhost:8080/api`
- **H2 Database Console**: `http://localhost:8080/h2-console`
- **Health Check**: `http://localhost:8080/api/health`

### Default Test Users

The application automatically creates test users on startup:

| Username | Password | Email |
|----------|----------|-------|
| testuser | password123 | test@example.com |
| admin | admin123 | admin@example.com |

## API Usage

### Authentication

First, obtain a JWT token by logging in:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }'
```

Use the returned token in subsequent requests:

```bash
curl -X GET http://localhost:8080/api/payments/user/me \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### Process a Payment

```bash
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "amount": 100.00,
    "currency": "USD",
    "paymentMethod": "CREDIT_CARD",
    "merchantReference": "ORDER-001",
    "description": "Test payment",
    "cardDetails": {
      "cardNumber": "4111111111111111",
      "expiryMonth": "12",
      "expiryYear": "2025",
      "cvv": "123",
      "cardHolderName": "John Doe"
    }
  }'
```

## Fraud Detection

The system includes a fraud detection engine that analyzes multiple risk factors:

### Risk Factors

**Amount Risk**: Higher payment amounts increase fraud score
- $15,000+: High risk (0.5)
- $5,000+: Medium risk (0.3)
- $1,000+: Low risk (0.1)

**Payment Method Risk**: Different payment methods have varying risk levels
- Cryptocurrency: Highest risk (0.2)
- Digital Wallet: Medium risk (0.08)
- Credit Card: Low risk (0.05)
- Bank Transfer: Lowest risk (0.0)

**Frequency Risk**: Too many payments in short time periods
- 3+ payments per hour: High risk (0.4)
- 10+ payments per day: Medium risk (0.3)

**User Behavior Risk**: Unusual patterns and new user activity
- New users: Increased risk (0.2)
- Payments significantly larger than user average: Variable risk

### Risk Thresholds

- **Low Risk** (< 0.2): Payment allowed
- **Medium Risk** (0.2 - 0.5): Payment allowed with monitoring
- **High Risk** (> 0.5): Payment blocked

### Configuration

Fraud detection settings can be modified in `FraudDetectionService.java`:

```java
private static final BigDecimal HIGH_AMOUNT_THRESHOLD = new BigDecimal("5000.00");
private static final BigDecimal VERY_HIGH_AMOUNT_THRESHOLD = new BigDecimal("15000.00");
private static final int MAX_PAYMENTS_PER_HOUR = 3;
private static final int MAX_PAYMENTS_PER_DAY = 10;
```

## Database Schema

### Key Entities

**Users**
- ID, username, email, password hash
- First name, last name, phone number
- Active status, created/updated timestamps

**Payments**
- ID, user reference, amount, currency
- Payment method, status, merchant reference
- Description, fraud score, timestamps

**Transactions**
- ID, payment reference, transaction type
- Amount, status, gateway response
- Gateway transaction ID, processed timestamp

### Relationships

- One User → Many Payments
- One Payment → Many Transactions
- All entities include audit timestamps

## Testing

### Run Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=PaymentServiceTest

# Run with coverage
mvn test jacoco:report
```

### Manual API Testing

Use the provided JavaScript test script for browser-based testing:

1. Open Chrome Developer Tools
2. Go to Console tab
3. Copy and paste the test script from the project
4. Run the script to test all endpoints

## Configuration

## API Documentation

Complete API documentation is available in `API_DOCUMENTATION.md`, including:

- All endpoint specifications
- Request/response examples
- Error codes and handling
- Authentication requirements

## Deployment

### Local Development

The application runs with H2 in-memory database for easy development and testing.

### Production Deployment

For production deployment:

1. **Database**: Replace H2 with PostgreSQL or MySQL
2. **Security**: Configure proper JWT secret and HTTPS
3. **Monitoring**: Add application monitoring and logging
4. **Environment**: Use environment-specific configuration

---

**Project Status**: Finished but not perfect.
**Last Updated**: June 2025