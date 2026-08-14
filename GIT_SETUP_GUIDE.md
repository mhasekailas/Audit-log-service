# Git Setup & Commit Guide

## Phase 1: Foundation Setup (Completed)

### Initialize Git Repository

```bash
cd "f:\Kailas SChwab assignment\Audit-log-service"

# Initialize git
git init

# Configure git (use your own GitHub credentials)
git config user.name "Your Name"
git config user.email "your.email@example.com"

# Add all files from Phase 1: Foundation
git add .
git commit -m "Initial commit: Project scaffold, architecture design, database schema

- Created Spring Boot backend project structure
- Set up React frontend with components
- Designed PostgreSQL schema with hash chain tables
- Created REST API controller and service layer
- Configured Docker Compose for local development
- Added comprehensive README with setup instructions

Phase: 1 (Foundation)
Artifacts:
- Backend: pom.xml, application properties, main app class
- Frontend: React components (EventForm, EventList, ChainVerification)
- Database: PostgreSQL schema with audit_events, redaction_log, compliance tables
- Documentation: Architecture overview and setup guide"
```

## Subsequent Commits (Plan)

After each major implementation phase, commit with messages like:

### Phase 2A: Hash Chain Implementation
```bash
git add backend/src/main/java/com/schwab/auditlog/util/HashUtil.java
git commit -m "Implement SHA-256 hash chain utilities

- HashUtil: Content hash and chain hash computation
- Genesis hash for first record in chain
- Hash verification methods for integrity checking
- Tests for hash functions

Phase: 2 (Scenario A Core)"
```

### Phase 2B: Repository & Service
```bash
git add backend/src/main/java/com/schwab/auditlog/repository/
git add backend/src/main/java/com/schwab/auditlog/service/
git commit -m "Implement AuditEventService and Repository

- AuditEventRepository: JPA queries for filtering and pagination
- AuditEventService: Create events, query, verify chain
- Chain verification logic with tampering detection
- Transaction management and error handling

Phase: 2 (Scenario A Core)"
```

### Phase 2C: REST API Controller
```bash
git add backend/src/main/java/com/schwab/auditlog/controller/
git commit -m "Implement REST API endpoints

- POST /audit/events: Create new events (Write API)
- GET /audit/events: Query with filtering and pagination
- GET /audit/verify: Chain integrity verification
- GET /audit/health: Health check endpoint
- OpenAPI/Swagger documentation

Phase: 2 (Scenario A Core)"
```

### Phase 2D: Testing
```bash
git add backend/src/test/
git commit -m "Add unit and integration tests for Scenario A

- AuditEventServiceTests: Happy path and error cases
- ChainVerificationTests: Tampering detection validation
- Integration tests: End-to-end event creation to verification
- Test coverage: >80% of critical paths

Phase: 2 (Scenario A Testing)"
```

### Phase 3: Scenario C
```bash
git add backend/src/main/java/com/schwab/auditlog/compliance/
git commit -m "Implement Scenario C: Compliance Reporting

- Requirement clarification document
- Compliance audit table schema
- ComplianceReportService: Generate reports by actor/resource/time
- API endpoints for compliance reporting
- Design documentation of trade-offs and assumptions

Phase: 3 (Scenario C)"
```

### Phase 4A: Retention & Archival
```bash
git add database/migration_retention.sql
git commit -m "Implement Scenario B: Retention and Archival

- Archive policies for records older than retention window
- ArchivalService: Move records to archived state
- Chain verification: Handle archived records correctly
- Tests: Verify no false positives on archived gaps

Phase: 4 (Scenario B Retention)"
```

### Phase 4B: Structured Redaction
```bash
git add backend/src/main/java/com/schwab/auditlog/redaction/
git commit -m "Implement Scenario B: Structured Redaction

- RedactionService: Redact sensitive fields in payload
- Redaction log: Track what was redacted and when
- Chain integrity: Redactions don't break hash verification
- Design doc: Redaction strategy and trade-offs

Phase: 4 (Scenario B Redaction)"
```

### Phase 4C: Bulk Export
```bash
git add backend/src/main/java/com/schwab/auditlog/export/
git commit -m "Implement Scenario B: Bulk Export

- BulkExportService: Export records by actor/resource
- Self-contained verifiable bundles with chain metadata
- Export hash for recipient verification
- Tests: Verify exported bundles are independently verifiable

Phase: 4 (Scenario B Export)"
```

### Phase 5: Documentation & Final
```bash
git add docs/
git add DESIGN_DECISIONS.md
git add AI_USAGE_LOG.md
git add ATTESTATION.md

git commit -m "Final: Documentation and engineering summary

Artifacts:
- ARCHITECTURE.md: Components, data model, design decisions
- DESIGN_DECISIONS.md: Rationale for major choices
- SETUP.md: Complete setup and deployment instructions
- API_DOCUMENTATION.md: OpenAPI spec and examples
- TESTING_STRATEGY.md: Coverage, limitations, trade-offs
- AI_USAGE_LOG.md: Traceability of AI assistance
- ENGINEERING_SUMMARY.md: Plan, rationale, risks, assumptions
- ATTESTATION.md: Integrity attestation

Phase: 5 (Final Delivery)"
```

## Connecting to GitHub

Once you have local commits:

```bash
# Add your GitHub repo as remote
git remote add origin https://github.com/yourusername/audit-log-service.git

# Rename branch if needed (GitHub defaults to main)
git branch -M main

# Push with history
git push -u origin main

# Verify history is available
git log --oneline
```

## Key Points

1. **Each commit** represents a logical step in development
2. **Commit messages** explain what was done and why (AI acceptance/rejection)
3. **Git history** demonstrates your engineering process
4. **Frequency**: Commit after each component/feature is complete
5. **Reviewers will see**: Architecture progression, design decisions, testing approach

This approach shows the assignment panel:
- How you broke down requirements
- When you used AI and how you directed it
- Your engineering judgment in reviewing/accepting changes
- Iterative development and testing discipline
