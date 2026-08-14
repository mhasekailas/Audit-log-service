# Phase 1: Foundation & Architecture Setup

## ✅ Completed Tasks

### 1. Technology Stack Selection
- **Backend**: Java 17 + Spring Boot 3.1.5
- **Frontend**: React 18 with Axios
- **Database**: PostgreSQL 15 with JSONB support
- **Hashing**: Built-in Java MessageDigest for SHA-256
- **Testing**: JUnit 5, Mockito (backend), Jest (frontend)
- **Containerization**: Docker Compose for local development

### 2. Project Structure Created

```
audit-log-service/
├── backend/                          # Spring Boot REST API
│   ├── src/main/java/com/schwab/auditlog/
│   │   ├── AuditLogServiceApplication.java  # Main app entry
│   │   ├── model/
│   │   │   └── AuditEvent.java              # JPA entity
│   │   ├── controller/
│   │   │   └── AuditLogController.java      # REST endpoints
│   │   ├── service/
│   │   │   └── AuditEventService.java       # Business logic
│   │   ├── repository/
│   │   │   └── AuditEventRepository.java    # Database access
│   │   ├── util/
│   │   │   └── HashUtil.java                # SHA-256 hashing
│   │   └── dto/                             # DTOs
│   │       ├── CreateEventRequest.java
│   │       ├── AuditEventResponse.java
│   │       └── ChainVerificationResponse.java
│   ├── src/main/resources/
│   │   └── application.properties    # Configuration
│   └── pom.xml                       # Maven dependencies
│
├── frontend/                          # React UI
│   ├── public/
│   │   └── index.html                # HTML entry point
│   ├── src/
│   │   ├── components/
│   │   │   ├── EventForm.js          # Create event form
│   │   │   ├── EventList.js          # List events with filtering
│   │   │   └── ChainVerification.js  # Verify chain integrity
│   │   ├── App.js                    # Main React component
│   │   ├── App.css                   # Styling
│   │   └── index.js                  # React entry point
│   └── package.json                  # npm dependencies
│
├── database/                          # Database setup
│   └── schema.sql                    # PostgreSQL schema
│
├── docker-compose.yml                # Local PostgreSQL
├── README.md                          # Setup & usage guide
├── GIT_SETUP_GUIDE.md               # Git commit strategy
├── .gitignore                         # Git exclusions
└── PHASE_1_FOUNDATION.md             # This file
```

### 3. Database Schema Designed

**Core Tables:**
- `audit_events`: Main table with hash chain fields
- `redaction_log`: Track field redactions (Scenario B)
- `compliance_audit_access`: Track access patterns (Scenario C)
- `bulk_exports`: Track export operations (Scenario B)
- `retention_policies`: Store archival policies (Scenario B)

**Key Fields:**
- `id`: Primary key (auto-increment)
- `eventType`, `actorId`, `resourceType`, `resourceId`: Event metadata
- `payload`: JSONB for flexible structured data
- `timestamp`: Server-assigned for ordering
- `content_hash`: SHA-256 of event fields
- `chain_hash`: SHA-256 of (previousChainHash + contentHash)
- `sequence_number`: Ensure ordering and chain integrity
- `is_archived`, `archived_at`: Soft-delete for Scenario B

**Indexes:**
- On query columns: actorId, eventType, resourceType, resourceId, timestamp
- On chain columns: sequence_number, is_archived
- For performance: sorted by sequence for rapid chain verification

### 4. Hash Chain Architecture

**Design Decisions:**

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Hash Algorithm | SHA-256 | Industry standard, FIPS-compliant, fast, secure |
| Hash Input | All event fields + timestamp | Detects any modification |
| Chain Structure | Sequential linking | Immutable, tamper-evident |
| Genesis | SHA256("GENESIS") | Deterministic, public knowledge acceptable |
| Timestamp | Server-assigned | Prevents clock-skew attacks, ensures ordering |

**Hash Chain Mechanics:**

```
Record 1:
  contentHash = SHA256("USER_LOGIN|user1|ACCOUNT|acc1|{...}|2026-08-14T10:00:00")
  chainHash = SHA256("SHA256(GENESIS)" + contentHash)

Record 2:
  contentHash = SHA256("RECORD_UPDATED|admin|ACCOUNT|acc1|{...}|2026-08-14T10:05:00")
  chainHash = SHA256(previousRecord.chainHash + contentHash)

Record N:
  [similar pattern continues]
```

**Verification Algorithm:**
1. Iterate through all non-archived records in sequence order
2. For each record, recompute: contentHash and chainHash
3. Compare recomputed hashes with stored hashes
4. On first mismatch, return breach info with record ID and violation type
5. Possible violations:
   - `CONTENT_MODIFIED`: Event fields were changed
   - `CHAIN_HASH_MISMATCH`: Hash link broken (record or previous record modified)

### 5. Core API Endpoints (Scenario A)

**Write API:**
```
POST /audit/events
Content-Type: application/json

Request:
{
  "eventType": "USER_LOGIN",
  "actorId": "user123",
  "resourceType": "ACCOUNT",
  "resourceId": "acc-456",
  "payload": { "ip": "192.168.1.1", "sessionId": "sess-789" }
}

Response (201 Created):
{
  "success": true,
  "data": {
    "id": 1,
    "eventType": "USER_LOGIN",
    "actorId": "user123",
    "resourceType": "ACCOUNT",
    "resourceId": "acc-456",
    "timestamp": "2026-08-14T10:00:00",
    "contentHash": "abc123...",
    "chainHash": "xyz789...",
    "sequenceNumber": 1
  }
}
```

**Query API:**
```
GET /audit/events?actorId=user123&eventType=USER_LOGIN&limit=50

Response:
{
  "success": true,
  "records": [...],
  "totalCount": 100,
  "pageNumber": 0,
  "pageSize": 50,
  "hasMore": true
}
```

**Chain Verification API:**
```
GET /audit/verify

Response (if valid):
{
  "success": true,
  "data": {
    "isValid": true,
    "totalRecords": 1000,
    "firstBreach": null
  }
}

Response (if tampered):
{
  "success": true,
  "data": {
    "isValid": false,
    "totalRecords": 1000,
    "firstBreach": {
      "recordId": 456,
      "expectedHash": "abc123...",
      "actualHash": "xyz789...",
      "violationType": "CONTENT_MODIFIED"
    }
  }
}
```

### 6. React Frontend UI Components

**EventForm Component:**
- Create new audit events
- Validate JSON payload
- Display success/error messages
- Navigate back to event list after creation

**EventList Component:**
- Display paginated list of events
- Filter by: actorId, eventType, resourceType, resourceId
- Show hash values (truncated) with tooltip
- Refresh button to reload data

**ChainVerification Component:**
- Trigger chain integrity verification
- Display results: valid or invalid
- Show breach details if tampering detected
- Display total record count

### 7. Design Decisions & Trade-offs

#### Decision 1: Server-Assigned Timestamps
- **Choice**: Server assigns timestamp on event creation
- **Why**: Prevents clock-skew attacks, ensures chronological ordering
- **Alternative Rejected**: Caller-supplied (security risk)
- **Trade-off**: Callers cannot specify event time; must use server time

#### Decision 2: Sequential Ordering
- **Choice**: Use auto-incrementing `sequence_number` for chain ordering
- **Why**: Simple, deterministic, no concurrent write conflicts
- **Alternative Considered**: UUID-based ordering (loses determinism)
- **Trade-off**: Slightly slower with very high throughput; acceptable for compliance use case

#### Decision 3: In-Memory Chain Verification
- **Choice**: Load all non-archived records into memory for verification
- **Why**: Simple to implement, ensures 100% chain integrity
- **Alternative Considered**: Checkpoint-based (faster, more complex)
- **Trade-off**: Doesn't scale to 100M+ records; acceptable for typical compliance window (e.g., 7 years = ~250M events at 1000/sec)

#### Decision 4: No Digital Signatures
- **Choice**: Hash chain only; no PKI/signing layer
- **Why**: Simplifies initial implementation; focus on tampering detection
- **Alternative Considered**: Ed25519 signatures (adds non-repudiation)
- **Trade-off**: Cannot prove audit logs weren't created/modified by admin; suitable for trusted environment

### 8. Security Assumptions

1. **Database is trusted**: Physical database security is managed
2. **Server clock is accurate**: NTP sync assumed
3. **Process isolation**: No rogue processes accessing database directly
4. **Network security**: HTTPS/TLS for API (to be added in production)
5. **Access control**: Authentication/authorization layer to be added (out of Phase 1 scope)

### 9. Limitations & Future Work

**Current Limitations:**
- Single-database (no distributed verification)
- In-memory chain verification (limits scale)
- No encryption at rest
- No digital signatures for non-repudiation
- No async operations

**Future Enhancements:**
- Add JWT/OAuth2 authentication
- Implement digital signatures (Ed25519)
- Incremental verification from checkpoint
- Multi-datacenter replication support
- GraphQL API
- Real-time WebSocket subscriptions
- Advanced retention policies with legal hold

## Next Steps (Phase 2)

1. **Add comprehensive unit tests** for hash functions and service layer
2. **Add integration tests** for end-to-end workflows
3. **Test tampering scenarios**: Modify a record in DB and verify detection
4. **Stress test**: Verify performance with 10K, 100K records
5. **Add error handling**: Proper exception classes and error responses

See: `GIT_SETUP_GUIDE.md` for commit strategy
See: `README.md` for setup instructions
