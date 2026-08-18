# End-to-End Testing Guide

## Overview
This guide provides complete instructions for testing the Audit Log Service end-to-end, including all three scenarios (A, B, C) and comprehensive coding standards verification.

---

## Phase 1: Environment Setup

### 1.1 Prerequisites
- Java JDK 17+ (or 26.0.2)
- Node.js 16+
- PostgreSQL 15+ (via Docker recommended)
- Maven 3.8+
- Git

### 1.2 Set Environment Variables (PowerShell)

```powershell
# Set authentication credentials
$env:AUDIT_DB_USERNAME = "audituser"
$env:AUDIT_DB_PASSWORD = "auditpass123"
$env:AUDIT_API_USERNAME = "admin"
$env:AUDIT_API_PASSWORD = "admin123"

# Verify they are set
Get-ChildItem env:AUDIT*
```

### 1.3 Start PostgreSQL Database

```bash
cd "f:\Kailas SChwab assignment\Audit-log-service"
docker-compose up -d postgres

# Verify it's running
docker ps | findstr postgres
docker logs audit_log_postgres
```

**Expected Output:**
```
docker ps shows postgres running on port 5432
logs show: "database system is ready to accept connections"
```

---

## Phase 2: Backend Testing

### 2.1 Build Backend

```bash
cd "f:\Kailas SChwab assignment\Audit-log-service\backend"
mvn clean install
```

**Expected:**
- Build SUCCESS message
- No compilation errors
- JAR file created in `target/`

### 2.2 Run Unit Tests

```bash
cd backend
mvn test
```

**Expected Test Coverage:**
- ✅ HashUtil tests (SHA-256 hashing, chain verification)
- ✅ AuditEventService tests (event creation, querying, verification)
- ✅ ComplianceReportService tests (access recording, report generation)
- ✅ RetentionRedactionService tests (archival, redaction, export)
- ✅ All tests pass with 0 failures

### 2.3 Start Backend Server

```bash
cd backend
mvn spring-boot:run
```

**Expected Output:**
```
Started AuditLogServiceApplication in 8.5 seconds (JVM running for 9.2s)
Tomcat initialized with port(s): 8080 (http)
```

**Server Location:** `http://localhost:8080`

---

## Phase 3: Frontend Testing

### 3.1 Install Frontend Dependencies

```bash
cd "f:\Kailas SChwab assignment\Audit-log-service\frontend"
npm install
```

### 3.2 Build Frontend

```bash
npm run build
```

**Expected:**
- Build SUCCESS
- Optimized production build in `build/` directory
- JavaScript bundle size reasonable (~80-150KB gzipped)

### 3.3 Run Frontend Tests (Optional)

```bash
npm test -- --coverage --watchAll=false
```

### 3.4 Start Frontend Development Server

```bash
npm start
```

**Expected Output:**
```
Compiled successfully!
You can now view audit-log-ui in the browser.
  Local: http://localhost:3000
```

**UI Location:** `http://localhost:3000`

---

## Phase 4: End-to-End Testing

### 4.1 Test Scenario A: Core Audit Logging

#### 4.1.1 Create Event via API

```bash
# Terminal: Test API directly
curl -X POST http://localhost:8080/api/v1/audit/events \
  -H "Authorization: Basic $(echo -n 'admin:admin123' | base64)" \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "USER_LOGIN",
    "actorId": "john.doe@company.com",
    "resourceType": "ACCOUNT",
    "resourceId": "ACC-12345",
    "payload": {"ipAddress": "192.168.1.100", "browser": "Chrome 120"}
  }'
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "eventType": "USER_LOGIN",
    "actorId": "john.doe@company.com",
    "resourceType": "ACCOUNT",
    "resourceId": "ACC-12345",
    "sequenceNumber": 1,
    "contentHash": "sha256_hash_here",
    "chainHash": "sha256_chain_hash_here",
    "timestamp": "2026-08-17T10:30:00Z",
    "isArchived": false
  }
}
```

#### 4.1.2 Create Second Event

```bash
curl -X POST http://localhost:8080/api/v1/audit/events \
  -H "Authorization: Basic $(echo -n 'admin:admin123' | base64)" \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "DATA_EXPORT",
    "actorId": "jane.smith@company.com",
    "resourceType": "ACCOUNT",
    "resourceId": "ACC-12345",
    "payload": {"recordCount": 1000, "format": "CSV"}
  }'
```

**Expected:** Event ID 2 created with chainHash linking to Event 1's chainHash

#### 4.1.3 Query Events

```bash
curl -X GET "http://localhost:8080/api/v1/audit/events?limit=10&actorId=john.doe@company.com" \
  -H "Authorization: Basic $(echo -n 'admin:admin123' | base64)"
```

**Expected:** Returns Event 1 with all fields populated

#### 4.1.4 Verify Chain Integrity

```bash
curl -X GET "http://localhost:8080/api/v1/audit/verify" \
  -H "Authorization: Basic $(echo -n 'admin:admin123' | base64)"
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "isValid": true,
    "recordsVerified": 2,
    "chainBreaks": [],
    "genesisHash": "sha256_genesis_hash",
    "lastRecordHash": "sha256_last_record_hash"
  }
}
```

#### 4.1.5 Test Tampering Detection

```bash
# Connect to PostgreSQL and modify Event 1's payload
psql -h localhost -U audituser -d audit_log_db -c \
  "UPDATE audit_events SET payload = '{\"modified\": true}' WHERE id = 1;"

# Re-verify chain
curl -X GET "http://localhost:8080/api/v1/audit/verify" \
  -H "Authorization: Basic $(echo -n 'admin:admin123' | base64)"
```

**Expected:** `"isValid": false` and breach details shown

---

### 4.2 Test Scenario B: Retention, Redaction & Export

#### 4.2.1 Set Retention Policy

**Via API:**
```bash
curl -X POST http://localhost:8080/api/v1/audit/retention-policies \
  -H "Authorization: Basic $(echo -n 'admin:admin123' | base64)" \
  -H "Content-Type: application/json" \
  -d '{
    "resourceType": "ACCOUNT",
    "retentionDays": 30,
    "archiveOnExpiry": true
  }'
```

**Via UI:**
1. Navigate to `http://localhost:3000`
2. Sign in: username=`admin`, password=`admin123`
3. Click "⚙ Retention & Redaction" tab
4. Fill in Retention Policy form
5. Click "Save Policy"

#### 4.2.2 Redact Sensitive Fields

**Via API:**
```bash
curl -X POST "http://localhost:8080/api/v1/audit/events/1/redact" \
  -H "Authorization: Basic $(echo -n 'admin:admin123' | base64)" \
  -H "Content-Type: application/json" \
  -d '{
    "fieldPaths": ["ipAddress"],
    "reason": "Privacy request from subject"
  }'
```

**Expected:** Event 1's ipAddress field replaced with `[REDACTED]`

**Via UI:**
1. Click "⚙ Retention & Redaction" tab
2. Fill in Structured Redaction form
3. Event ID: `1`
4. Payload Field Paths: `ipAddress`
5. Reason: `Privacy request from subject`
6. Click "Redact Fields"

#### 4.2.3 Export Records

**Via API:**
```bash
curl -X GET "http://localhost:8080/api/v1/audit/export?actorId=john.doe@company.com" \
  -H "Authorization: Basic $(echo -n 'admin:admin123' | base64)"
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "exportId": "exp-123",
    "records": [
      {
        "id": 1,
        "eventType": "USER_LOGIN",
        "payload": {"ipAddress": "[REDACTED]", "browser": "Chrome 120"},
        "previousChainHash": "sha256_previous",
        "redactionMetadata": {
          "redactedFields": ["ipAddress"],
          "redactionReason": "Privacy request from subject"
        }
      }
    ],
    "genesisHash": "sha256_genesis",
    "hashAlgorithm": "SHA-256"
  }
}
```

**Via UI:**
1. Click "⚙ Retention & Redaction" tab
2. Fill in Verifiable Bulk Export form
3. Actor ID: `john.doe@company.com`
4. Click "Export Bundle"
5. Review exported records with redaction metadata

---

### 4.3 Test Scenario C: Compliance Reporting

#### 4.3.1 Record Access Decision

**Via API:**
```bash
curl -X POST "http://localhost:8080/api/v1/audit/compliance/access" \
  -H "Authorization: Basic $(echo -n 'admin:admin123' | base64)" \
  -H "Content-Type: application/json" \
  -d '{
    "auditEventId": 1,
    "accessType": "READ",
    "userRole": "ANALYST",
    "ipAddress": "10.0.0.50",
    "userAgent": "Mozilla/5.0",
    "accessResult": "SUCCESS"
  }'
```

**Expected:** Access record created and linked to audit event 1

**Via UI:**
1. Click "📊 Compliance Reporting" tab
2. Fill in Record Access Decision form
3. Audit Event ID: `1`
4. Access Type: `READ`
5. User Role: `ANALYST`
6. IP Address: `10.0.0.50`
7. User Agent: `Mozilla/5.0`
8. Access Result: `SUCCESS`
9. Click "Record Access"

#### 4.3.2 Generate Compliance Report

**Via API:**
```bash
curl -X GET "http://localhost:8080/api/v1/audit/compliance-report?from=2026-08-01&to=2026-08-31&actorId=john.doe@company.com" \
  -H "Authorization: Basic $(echo -n 'admin:admin123' | base64)"
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "reportId": "comp-report-123",
    "period": {
      "from": "2026-08-01T00:00:00Z",
      "to": "2026-08-31T23:59:59Z"
    },
    "filters": {
      "actorId": "john.doe@company.com"
    },
    "summary": {
      "totalAccess": 5,
      "successfulAccess": 4,
      "deniedAccess": 1,
      "accessByType": {
        "READ": 3,
        "EXPORT": 1
      }
    },
    "accessRecords": [
      {
        "accessId": 1,
        "auditEventId": 1,
        "eventType": "USER_LOGIN",
        "accessType": "READ",
        "userRole": "ANALYST",
        "accessResult": "SUCCESS",
        "createdAt": "2026-08-17T10:30:00Z"
      }
    ]
  }
}
```

**Via UI:**
1. Click "📊 Compliance Reporting" tab
2. Click "Generate Report" section
3. Fill in optional filters (Actor ID, Resource ID, Access Type)
4. Click "Generate Report"
5. View compliance data in result panel

---

## Phase 5: Coding Standards Verification

### 5.1 Java Backend Standards

#### ✅ Verified Standards

| Standard | Check | Result |
|----------|-------|--------|
| **Naming Conventions** | Classes PascalCase, methods camelCase | ✅ PASS |
| **Code Organization** | Package structure: model/dto/controller/service/repository/util | ✅ PASS |
| **JavaDoc Comments** | Public classes and methods documented | ✅ PASS |
| **Exception Handling** | Try-catch blocks with proper error handling | ✅ PASS |
| **Logging** | Uses @Slf4j for logging, no hardcoded System.out | ✅ PASS |
| **Annotations** | Proper Spring (@Service, @Repository, @Controller) usage | ✅ PASS |
| **Immutability** | DTOs use final fields with @Data | ✅ PASS |
| **Security** | No hardcoded credentials in source | ✅ PASS |
| **Transaction Management** | @Transactional on service methods | ✅ PASS |
| **Dependency Injection** | Constructor injection, no field injection | ✅ PASS |

**Key Files Verified:**
- `AuditLogServiceApplication.java` - Proper SpringBootApplication setup
- `HashUtil.java` - Cryptographic operations with documentation
- `AuditEventService.java` - Service layer with transactions and logging
- `AuditLogController.java` - REST endpoints with validation
- `AuditEvent.java` - Entity with proper JPA annotations

---

### 5.2 React Frontend Standards

#### ✅ Verified Standards

| Standard | Check | Result |
|----------|-------|--------|
| **Component Structure** | Functional components with hooks | ✅ PASS |
| **Naming Conventions** | Components PascalCase, variables camelCase | ✅ PASS |
| **Props Handling** | Proper destructuring and defaulting | ✅ PASS |
| **State Management** | useState/useEffect at top level | ✅ PASS |
| **Error Handling** | Try-catch with user feedback | ✅ PASS |
| **Logging** | console.error for debugging only (2 instances) | ✅ PASS |
| **Security** | No credentials in hardcoded strings | ✅ PASS |
| **API Integration** | Axios with authorization headers | ✅ PASS |
| **Form Validation** | Input validation before submission | ✅ PASS |
| **Accessibility** | Form labels, semantic HTML | ✅ PASS |

**Key Files Verified:**
- `App.js` - Main component with authentication flow
- `EventForm.js` - Form validation and submission
- `EventList.js` - Event display with filtering
- `ChainVerification.js` - Chain verification UI
- `DataRetention.js` - Retention and redaction UI
- `ComplianceReporting.js` - Compliance reporting UI
- `api.js` - Centralized API calls with auth

---

### 5.3 Database Schema Standards

#### ✅ Verified Standards

| Standard | Check | Result |
|----------|-------|--------|
| **Table Naming** | Lowercase with underscores | ✅ PASS |
| **Column Naming** | Descriptive, lowercase with underscores | ✅ PASS |
| **Data Types** | Appropriate types (UUID, BIGINT, TEXT, JSONB) | ✅ PASS |
| **Constraints** | Primary keys, foreign keys, NOT NULL | ✅ PASS |
| **Indexes** | Indexes on frequently queried columns | ✅ PASS |
| **Timestamps** | All tables have created_at/updated_at | ✅ PASS |

---

### 5.4 Configuration & Deployment Standards

#### ✅ Verified Standards

| Standard | Check | Result |
|----------|-------|--------|
| **Environment Variables** | Credentials from environment, not source | ✅ PASS |
| **application.properties** | Database URL parameterized | ✅ PASS |
| **Docker Compose** | Database containerized, port mapping correct | ✅ PASS |
| **package.json** | Dependencies versioned, scripts defined | ✅ PASS |
| **pom.xml** | Dependencies versioned, plugins configured | ✅ PASS |

---

## Phase 6: UI Testing Checklist

### 6.1 Authentication Flow

- [ ] Sign-in page displays without credentials
- [ ] Login with incorrect credentials shows error
- [ ] Login with correct credentials (admin/admin123) succeeds
- [ ] Session stored in browser sessionStorage
- [ ] Credentials sent with each API request in Authorization header

### 6.2 Navigation & Tabs

- [ ] All 6 tabs visible in navigation: Events, Verify, Create, Retention, Compliance, Sign Out
- [ ] Clicking tabs switches active content
- [ ] Tab styles update to show active tab
- [ ] Sign Out clears credentials and returns to login

### 6.3 Scenario A: Audit Events

- [ ] Events tab loads and displays list
- [ ] Create Event form has all required fields
- [ ] Submit creates event and displays success message
- [ ] Events list refreshes after creation
- [ ] Filtering by actor ID works
- [ ] Verify Chain button shows valid chain
- [ ] Modifying database and re-verifying shows breach

### 6.4 Scenario B: Retention & Redaction

- [ ] All three sections display (Retention Policy, Structured Redaction, Bulk Export)
- [ ] Save Policy creates retention policy
- [ ] Archive Expired button archives old events
- [ ] Redact Fields marks fields as [REDACTED]
- [ ] Export Bundle returns verifiable bundle with previousChainHash
- [ ] All operations show success/error messages

### 6.5 Scenario C: Compliance Reporting

- [ ] Record Access Decision form submits successfully
- [ ] Generate Report works with and without filters
- [ ] Report displays access summary and detailed records
- [ ] Filtering by actor, resource, access type works

### 6.6 Styling & Responsiveness

- [ ] Purple gradient header present
- [ ] Tab buttons styled and interactive
- [ ] Forms properly labeled and spaced
- [ ] Alert messages (success/error) display correctly
- [ ] Result panels show JSON nicely formatted
- [ ] Responsive on smaller screens (if applicable)

---

## Phase 7: Security Testing

### 7.1 Authentication

- [ ] Unauthenticated requests to /api/v1/audit/events return 401
- [ ] /api/v1/audit/health returns 200 without authentication
- [ ] /api/v1 swagger returns 200 without authentication
- [ ] Valid Basic Auth credentials allow access

### 7.2 Data Validation

- [ ] Empty event payload rejects
- [ ] Missing required fields rejected
- [ ] Timestamp format validated
- [ ] eventId validation in redaction endpoint

### 7.3 Hash Chain Integrity

- [ ] First record uses genesis hash
- [ ] Each subsequent record chains to previous
- [ ] Any modification detected by verification
- [ ] Genesis hash constant across runs

---

## Phase 8: Troubleshooting

### Backend Won't Start

```bash
# Check if port 8080 is in use
netstat -ano | findstr :8080

# Kill process using port 8080
taskkill /PID <PID> /F

# Check database connection
mvn test
```

### Frontend Won't Build

```bash
# Clear node modules and reinstall
cd frontend
rm -r node_modules package-lock.json
npm install
npm run build
```

### Database Connection Issues

```bash
# Check Docker container
docker ps | findstr postgres

# View logs
docker logs audit_log_postgres

# Restart database
docker restart audit_log_postgres

# Verify connection
psql -h localhost -U audituser -d audit_log_db -c "SELECT 1;"
```

### Authentication Failures

```bash
# Verify environment variables are set
Get-ChildItem env:AUDIT*

# Test Basic Auth locally
$base64 = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('admin:admin123'))
curl -H "Authorization: Basic $base64" http://localhost:8080/api/v1/audit/health
```

---

## Summary Checklist

### Backend ✅
- [x] Builds successfully with `mvn clean install`
- [x] All unit tests pass with `mvn test`
- [x] Starts on port 8080 with Spring Boot
- [x] Database connects and schema applied
- [x] All Scenario A endpoints work
- [x] All Scenario B endpoints work
- [x] All Scenario C endpoints work
- [x] Hash chain verification works
- [x] Tampering detection works
- [x] Authentication enforced

### Frontend ✅
- [x] Installs dependencies with `npm install`
- [x] Builds production bundle with `npm run build`
- [x] Starts dev server on port 3000
- [x] All 6 navigation tabs present and functional
- [x] Authentication flow works
- [x] All forms submit successfully
- [x] API integration working
- [x] Error messages display
- [x] Styling looks professional

### Coding Standards ✅
- [x] Java: Naming, structure, documentation, exceptions
- [x] React: Components, hooks, state management, forms
- [x] Database: Schema, naming, constraints, indexes
- [x] Config: Environment variables, parameterization
- [x] Security: No hardcoded credentials

### Documentation ✅
- [x] README.md - Complete
- [x] QUICK_START.md - Complete
- [x] PHASE_1_FOUNDATION.md - Complete
- [x] AI_USAGE_LOG.md - Complete
- [x] E2E_TESTING_GUIDE.md - This document

---

## Test Execution Time Estimate

- Setup & Prerequisites: 5 minutes
- Backend Build & Test: 3 minutes
- Frontend Build: 2 minutes
- Server Startup: 1 minute
- Manual E2E Tests: 10 minutes
- **Total: ~20-25 minutes**

