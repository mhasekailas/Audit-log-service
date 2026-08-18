# Audit Log Service

A tamper-evident audit log service built with Java Spring Boot, React, and PostgreSQL. This system records an append-only history of events and guarantees that past records cannot be modified or deleted without detection using a cryptographic hash chain.

## Features

## Security Configuration

Audit and compliance endpoints require HTTP Basic authentication with role-based
access control. Credentials are read from environment variables and are never
stored in source files. Three roles are provisioned, each additive:

- `AUDIT_READER` - read-only (query, verify, export, stats)
- `AUDIT_WRITER` - reader + create events, redact, run retention archival
- `AUDIT_ADMIN` - writer + retention policy configuration and compliance reporting

```powershell
$env:AUDIT_DB_USERNAME = "your-db-user"
$env:AUDIT_DB_PASSWORD = "your-db-password"
$env:AUDIT_READER_USERNAME = "your-reader-user"
$env:AUDIT_READER_PASSWORD = "your-reader-password"
$env:AUDIT_API_USERNAME = "your-writer-user"
$env:AUDIT_API_PASSWORD = "your-writer-password"
$env:AUDIT_ADMIN_USERNAME = "your-admin-user"
$env:AUDIT_ADMIN_PASSWORD = "your-admin-password"
```

The React UI asks for API credentials at sign-in and keeps them only in the
browser session. Health and Swagger metadata remain public; audit data endpoints
require authentication and the appropriate role. Requests are also rate-limited
per client (default 60 requests/minute, see `audit.security.rate-limit.*` in
`application.properties`) and `POST /audit/events` supports an optional
`Idempotency-Key` header to safely retry/replay a submission without creating a
duplicate event.

### Scenario A: Core Audit Log Service
- **Write API**: Append-only event storage with automatic hash chain generation
- **Query API**: Retrieve events with filtering by actorId, eventType, resourceType, resourceId, and time range
- **Pagination**: Support for large result sets with cursor-based pagination
- **Hash Chain Verification**: Cryptographic verification of chain integrity with tampering detection

### Scenario B: Retention & Redaction
- **Retention Policies**: Archive records older than configurable retention window
- **Structured Redaction**: Redact sensitive fields without breaking the hash chain
- **Bulk Export**: Export verifiable bundles of records for compliance and archival

Scenario B endpoints:

```text
POST /api/v1/audit/retention-policies
POST /api/v1/audit/retention/archive?asOf=2026-08-14T12:00:00
POST /api/v1/audit/events/{id}/redact
GET  /api/v1/audit/export?actorId=user123
GET  /api/v1/audit/export?resourceId=acct-123
```

Redaction replaces selected object payload fields with `[REDACTED]`, records the
field path, reason, and one-way hash of the original value in `redaction_log`,
then recomputes content and chain hashes from the redacted record forward. This
preserves tamper evidence while ensuring the sensitive value is no longer stored.
Verification includes archived records, so retention does not create a false
chain break. Exports include each record's `previousChainHash`, the predecessor
chain hash outside the filtered set, the hash algorithm, and the genesis hash.

### Scenario C: Compliance Reporting

#### Requirement Clarification
The phrase "audit access to client account data" is normalized here as:

> Record every access decision for an audit event representing client account data,
> including access type, result, role, IP address, user agent, actor, resource, and
> access time; provide regulator-readable reports filtered by actor, account, access
> type, and time range.

#### Ambiguities and Assumptions
- **Who is the accessor?** This phase accepts a supplied role and actor-linked audit event; authentication/identity binding is out of scope.
- **What counts as access?** `READ`, `EXPORT`, and other caller-defined access types are accepted.
- **What is the report format?** JSON is implemented; CSV/PDF regulator templates are scoped out.
- **What time governs reporting?** The access decision's `created_at` timestamp is used.
- **How are denied attempts handled?** They are retained and counted separately from successful access.

#### Implemented Design
```text
POST /api/v1/audit/compliance/access
GET  /api/v1/audit/compliance-report?from=...&to=...&actorId=...&resourceId=...&accessType=...
```

The access table references an existing audit event. Reports join access records
to immutable event metadata and return totals, success/denied counts, counts by
access type, and detailed records. Authentication, authorization enforcement,
scheduled delivery, and regulator-specific formatting remain outside this phase.

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
GET /audit/events?actorId=user123&eventType=USER_LOGIN&from=2026-08-14T00:00:00&to=2026-08-14T23:59:59&limit=50&page=0

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

### Timestamp Handling: Caller-Supplied or Server-Assigned
- **Choice**: Accept a caller-supplied timestamp; assign server time when omitted
- **Rationale**: Supports historical event ingestion while providing a reliable default
- **Normalization**: Timestamps are normalized to PostgreSQL microsecond precision before hashing

### Redaction Strategy: Separate Audit Log
- **Choice**: Replace the selected value, log its one-way hash, and rebuild the chain from that record forward
- **Rationale**: Removes sensitive data while keeping authorized redaction verifiable
- **Alternative Considered**: Preserve the original value encrypted (adds key-management and deletion obligations)
- **Trade-off**: Historical pre-redaction values cannot be recovered; redaction is an authorized chain rewrite recorded in `redaction_log`

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
