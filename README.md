# Audit Log Service

A tamper-evident audit log service built with Java Spring Boot, React, and PostgreSQL. This system records an append-only history of events and guarantees that past records cannot be modified or deleted without detection using a cryptographic hash chain.

## Features

### Scenario A: Core Audit Log Service
- **Write API**: Append-only event storage with automatic hash chain generation
- **Query API**: Retrieve events with filtering by actorId, eventType, resourceType, resourceId, and time range
- **Pagination**: Support for large result sets with cursor-based pagination
- **Hash Chain Verification**: Cryptographic verification of chain integrity with tampering detection

### Scenario B: Retention & Redaction
- **Retention Policies**: Archive records older than configurable retention window
- **Structured Redaction**: Redact sensitive fields without breaking the hash chain
- **Bulk Export**: Export verifiable bundles of records for compliance and archival

### Scenario C: Compliance Reporting
- **Access Audit Trail**: Track who accessed what resources and when
- **Compliance Reports**: Generate regulatory compliance reports by actor, resource, or time range
- **Audit Context**: Capture access patterns, IP addresses, and user roles

## Architecture

```
audit-log-service/
├── backend/                 # Java Spring Boot REST API
│   ├── src/main/
│   │   ├── java/com/schwab/auditlog/
│   │   │   ├── AuditLogServiceApplication.java
│   │   │   ├── model/
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── repository/
│   │   │   └── util/
│   │   └── resources/
│   │       └── application.properties
│   └── pom.xml
├── frontend/                # React UI
│   ├── src/
│   │   ├── components/
│   │   ├── App.js
│   │   └── App.css
│   └── package.json
├── database/                # PostgreSQL schema
│   └── schema.sql
├── docker-compose.yml       # Local dev environment
└── README.md
```

## Tech Stack

- **Backend**: Java 17, Spring Boot 3.1.5, Spring Data JPA
- **Frontend**: React 18, Axios, Tailwind CSS
- **Database**: PostgreSQL 15 with JSONB support
- **Hashing**: SHA-256 for cryptographic chain verification
- **Testing**: JUnit 5, Mockito, Jest, React Testing Library

## Prerequisites

- Java 17+
- Maven 3.8+
- Node.js 16+
- npm or yarn
- Docker & Docker Compose (for PostgreSQL)
- Git

## Quick Start

### 1. Clone the Repository
```bash
git clone https://github.com/yourusername/audit-log-service.git
cd audit-log-service
```

### 2. Start PostgreSQL
```bash
docker-compose up -d postgres
```

The database will initialize with the schema automatically.

### 3. Build & Run Backend

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

The API will be available at `http://localhost:8080/api/v1`

API Documentation: `http://localhost:8080/api/v1/swagger-ui.html`

### 4. Build & Run Frontend

```bash
cd frontend
npm install
npm start
```

The UI will open at `http://localhost:3000`

## API Endpoints

### Write Events
```
POST /audit/events
Content-Type: application/json

{
  "eventType": "USER_LOGIN",
  "actorId": "user123",
  "resourceType": "ACCOUNT",
  "resourceId": "acc-456",
  "payload": {
    "ip": "192.168.1.1",
    "sessionId": "sess-789"
  }
}
```

### Query Events
```
GET /audit/events?actorId=user123&eventType=USER_LOGIN&limit=50&offset=0

Response:
{
  "records": [...],
  "totalCount": 100,
  "hasMore": true
}
```

### Verify Chain Integrity
```
GET /audit/verify

Response (if valid):
{
  "isValid": true,
  "totalRecords": 1000,
  "firstBreach": null
}

Response (if broken):
{
  "isValid": false,
  "totalRecords": 1000,
  "firstBreach": {
    "recordId": 456,
    "expectedHash": "abc123...",
    "actualHash": "xyz789...",
    "violationType": "CONTENT_MODIFIED"
  }
}
```

## Hash Chain Design

Each audit event includes:
- **contentHash**: SHA-256 hash of the event record (eventType, actorId, resourceType, resourceId, payload, timestamp)
- **chainHash**: SHA-256 hash of the previous record's chainHash + current record's contentHash

This creates an immutable chain where modifying any past record invalidates its hash and every subsequent hash.

**Genesis Record**: The first record uses a defined genesis hash (e.g., `"GENESIS"` hashed as SHA-256).

## Testing

### Backend Tests
```bash
cd backend
mvn test
```

### Frontend Tests
```bash
cd frontend
npm test
```

### Scenario A: Tampering Detection
```bash
# 1. Create an event
curl -X POST http://localhost:8080/api/v1/audit/events \
  -H "Content-Type: application/json" \
  -d '{"eventType":"USER_LOGIN","actorId":"user1","resourceType":"ACCOUNT","resourceId":"acc1","payload":{}}'

# 2. Verify chain (should be valid)
curl http://localhost:8080/api/v1/audit/verify

# 3. Modify the event in database (simulate tampering)
# (Connect to PostgreSQL and UPDATE the payload or timestamp)

# 4. Verify chain again (should detect breach)
curl http://localhost:8080/api/v1/audit/verify
```

## Security Considerations

1. **Hash Collision**: Uses SHA-256 (FIPS-compliant) with negligible collision probability
2. **Immutability**: Events are append-only; no update/delete APIs exposed
3. **Integrity**: Chain verification walks entire chain to detect any tampering
4. **Redaction**: Redacted fields tracked separately without breaking chain
5. **Access Control**: Consider adding authentication/authorization layer (JWT, OAuth2) before production
6. **Audit Trail**: Compliance table tracks who accessed audit records

## Design Decisions & Trade-offs

### Hash Algorithm: SHA-256
- **Choice**: SHA-256 (FIPS 140-2 compliant)
- **Rationale**: Industry standard, fast, secure against pre-image attacks
- **Alternative Considered**: SHA-512 (longer, slower), Argon2 (memory-hard, overkill for hashing)
- **Trade-off**: 256-bit hash is sufficient for cryptographic integrity

### Timestamp Handling: Server-Assigned
- **Choice**: Server generates timestamp on record creation
- **Rationale**: Prevents clock-skew attacks; ensures chronological ordering
- **Alternative**: Caller-supplied (rejected due to security/ordering risks)

### Redaction Strategy: Separate Audit Log
- **Choice**: Store original hash; track redactions in separate table
- **Rationale**: Preserves chain integrity while satisfying privacy requirements
- **Alternative Considered**: Redaction envelope (adds complexity)
- **Trade-off**: Requires two-table lookup for redacted fields

### Chain Genesis
- **Choice**: First record uses fixed genesis hash `SHA256("GENESIS")`
- **Rationale**: Simple, deterministic, no special case handling
- **Alternative**: Empty string or random seed
- **Trade-off**: Genesis hash is public knowledge (acceptable for this use case)

## Assumptions & Limitations

### Assumptions
1. Database is trusted and operates within a secure environment
2. Server clock is accurate (NTP synchronized)
3. Callers can be authenticated and authorized (to be added in production)
4. SHA-256 collision resistance is adequate for compliance window
5. Sequential ordering by ID is sufficient (no concurrent write conflicts)

### Limitations
1. **No Distributed Chain**: Single database source of truth
   - Future: Could implement distributed consensus for multi-region scenarios
2. **No Key Management**: No signing/encryption layer
   - Future: Add digital signatures for non-repudiation
3. **In-Memory Verification**: Chain verification loads all records
   - Optimization: Implement incremental verification from last known state
4. **No Async Redaction**: Redaction is synchronous
   - Future: Async job for bulk redaction operations
5. **Basic Retention**: Simple age-based archival
   - Future: Policy-based retention with legal hold support

## Development Roadmap

- [ ] Add JWT authentication/authorization
- [ ] Implement digital signatures for non-repudiation
- [ ] Add incremental chain verification (from checkpoint)
- [ ] Support for multi-datacenter replication
- [ ] GraphQL API in addition to REST
- [ ] Real-time WebSocket subscriptions for event streaming
- [ ] Advanced compliance report builder UI
- [ ] Performance optimizations for million+ record datasets

## Monitoring & Operations

### Health Check
```bash
curl http://localhost:8080/api/v1/actuator/health
```

### Metrics
- Event write throughput (events/second)
- Query response time (percentiles)
- Chain verification time (by record count)
- Redaction audit trail hits
- Archive job completion time

## Support & Contributing

This is a confidential Charles Schwab assignment. Do not share, redistribute, or use outside the scope of the assignment.

For questions during development, refer to the assignment document or consult your technical interviewer.

---

**Last Updated**: 2026-08-14  
**Version**: 1.0.0  
**Status**: In Development
