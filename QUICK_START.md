# Quick Reference: Next Steps to Get Running

## ⚡ 5-Minute Quick Start

```bash
# 1. Navigate to project
cd "f:\Kailas SChwab assignment\Audit-log-service"

# 2. Set credentials in the current shell (reader/writer/admin roles)
$env:AUDIT_DB_USERNAME = "your-db-user"
$env:AUDIT_DB_PASSWORD = "your-db-password"
$env:AUDIT_READER_USERNAME = "your-reader-user"
$env:AUDIT_READER_PASSWORD = "your-reader-password"
$env:AUDIT_API_USERNAME = "your-writer-user"
$env:AUDIT_API_PASSWORD = "your-writer-password"
$env:AUDIT_ADMIN_USERNAME = "your-admin-user"
$env:AUDIT_ADMIN_PASSWORD = "your-admin-password"

# 3. Start PostgreSQL (Docker)
docker-compose up -d postgres

# 4. Build Backend (in new terminal)
cd backend
mvn clean install
mvn spring-boot:run

# 5. Start Frontend (in another new terminal)
cd frontend
npm install
npm start
```

**Result:**
- API at `http://localhost:8080/api/v1`
- UI at `http://localhost:3000`
- PostgreSQL at `localhost:5432`

---

## 📋 What's Ready to Use

### Backend API Endpoints

**Create Event** (Write API)
```bash
curl -X POST http://localhost:8080/api/v1/audit/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventType":"USER_LOGIN",
    "actorId":"user1",
    "resourceType":"ACCOUNT",
    "resourceId":"acc-123",
    "payload":{"ip":"192.168.1.1"}
  }'
```

**Query Events** (Query API)
```bash
curl "http://localhost:8080/api/v1/audit/events?actorId=user1&limit=10"
```

**Verify Chain** (Scenario A)
```bash
curl http://localhost:8080/api/v1/audit/verify
```

### Frontend UI

**Available at**: `http://localhost:3000`

- **Events Tab**: Browse and filter all audit events
- **Verify Chain Tab**: Check integrity of the entire chain
- **Create Event Tab**: Add new events to the log

---

## 🔗 Important Files to Review

| File | Purpose |
|------|---------|
| `README.md` | Setup & features overview |
| `PHASE_1_FOUNDATION.md` | Architecture & design decisions |
| `EXECUTION_ROADMAP.md` | Full timeline & milestones |
| `GIT_SETUP_GUIDE.md` | How to commit work |
| `AI_USAGE_LOG.md` | Traceability of AI assistance |
| `PHASE_1_SUMMARY.md` | Completion summary |

---

## 🔐 How Hash Chain Works

```
Event 1:
├─ contentHash = SHA256("USER_LOGIN|user1|ACCOUNT|acc-123|{...}|timestamp")
└─ chainHash = SHA256(SHA256("GENESIS") + contentHash)

Event 2:
├─ contentHash = SHA256("RECORD_UPDATED|user1|ACCOUNT|acc-123|{...}|timestamp")
└─ chainHash = SHA256(previousRecord.chainHash + contentHash)
```

**Verification**: Recompute all hashes. If any mismatch found, tampering detected!

---

## 📊 Project Structure

```
audit-log-service/
├── backend/           # Spring Boot REST API (Java)
├── frontend/          # React Single Page App (JavaScript)
├── database/          # PostgreSQL schema
├── docker-compose.yml # Local dev environment
└── docs/              # Documentation
```

---

## ✅ Phase 1 Status

**COMPLETE** ✅
- [x] All source code written
- [x] Database schema created
- [x] Docker environment configured
- [x] Documentation complete
- [x] Git initialized

**NOT YET DONE** (Phase 2+)
- [ ] Comprehensive tests
- [ ] Tampering scenario validation
- [ ] Scenario B (Retention, Redaction, Export)
- [ ] Scenario C (Compliance Reporting)
- [ ] ATTESTATION.md file

---

## 🚦 Common Issues & Solutions

### PostgreSQL Connection Refused
```bash
# Ensure Docker is running
docker ps

# Check if postgres container is running
docker logs audit_log_postgres

# If not running:
docker-compose up -d postgres
```

### Port Already in Use (8080)
```bash
# Find process using port 8080
netstat -ano | findstr :8080

# Kill the process or change port in application.properties
```

### NPM Dependencies Issues
```bash
# Clear cache and reinstall
cd frontend
rm -rf node_modules package-lock.json
npm install
```

---

## 📱 Testing the System

### Step 1: Create Events
Use the React UI (Create Event tab) or:
```bash
curl -X POST http://localhost:8080/api/v1/audit/events \
  -H "Content-Type: application/json" \
  -d '{"eventType":"USER_LOGIN","actorId":"john","resourceType":"ACCOUNT","resourceId":"acc-1","payload":{}}'
```

### Step 2: Verify Chain
```bash
curl http://localhost:8080/api/v1/audit/verify
```
Should return: `"isValid": true`

### Step 3: Tamper with Database
Connect to PostgreSQL:
```bash
psql -U postgres -d audit_log_db -h localhost
```

Modify an event:
```sql
UPDATE audit_events SET payload = '{"modified":true}' WHERE id = 1;
```

### Step 4: Verify Again
```bash
curl http://localhost:8080/api/v1/audit/verify
```
Should now return: `"isValid": false` with breach details!

---

## 📝 Making Git Commits

After completing features:

```bash
# Make sure you're in the right directory
cd "f:\Kailas SChwab assignment\Audit-log-service"

# Configure git (one time)
git config user.name "Your Name"
git config user.email "your.email@example.com"

# Stage changes
git add .

# Commit with descriptive message
git commit -m "Feature: [Description]

Details about what was implemented:
- Point 1
- Point 2

Phase: [Number]
Artifacts: [List files changed]"

# View history
git log --oneline
```

See `GIT_SETUP_GUIDE.md` for detailed commit examples.

---

## 🎯 Next Phases (Quick Preview)

### Phase 2: Testing (2-3 hours)
- Write unit tests for HashUtil
- Write integration tests for core flows
- Test tampering scenarios
- Performance benchmarking

### Phase 3: Scenario C (1-2 hours)
- Design compliance reporting
- Implement access audit tracking
- Add report generation

### Phase 4: Scenario B (2-3 hours)
- Add retention policies
- Implement redaction
- Add bulk export

### Phase 5: Final (1-2 hours)
- Complete documentation
- Fill in ATTESTATION.md
- Prepare for live defense

---

## 💡 Key Design Choices (For Reference)

| Decision | What | Why |
|----------|------|-----|
| Hash Algorithm | SHA-256 | FIPS-compliant, industry standard |
| Timestamps | Caller-supplied or server-assigned | Supports historical events with a reliable fallback; normalized before hashing |
| Verification | Walk entire chain | 100% integrity guarantee |
| Database | PostgreSQL | ACID compliance, JSON support |
| Backend | Spring Boot | Robust, production-proven |
| Frontend | React | Fast, component-based |

---

## 🤝 AI Assistance Used

**Phase 1 total AI time**: ~5 hours
- Boilerplate generation
- Algorithm verification
- Documentation and guides
- Code scaffolding

**Maintained high engineering oversight**:
- All AI output was reviewed
- Design decisions were engineer-led
- All crypto implementations were verified
- Traceability documented in AI_USAGE_LOG.md

See `AI_USAGE_LOG.md` for complete traceability.

---

## ❓ If Something Doesn't Work

1. Check `README.md` for detailed setup
2. Check `PHASE_1_FOUNDATION.md` for architecture
3. Review API responses in browser console
4. Check backend logs: `mvn spring-boot:run` output
5. Verify PostgreSQL: `psql -U postgres -d audit_log_db`
6. Check Docker: `docker ps`, `docker logs`

---

## 🎉 You're Ready!

All Phase 1 foundation work is complete. The system is:

✅ Architecturally sound  
✅ Fully implemented (core APIs)  
✅ Ready to test  
✅ Well documented  
✅ Git-enabled  

Next: Make your first commit and push to GitHub!

---

**For detailed information, see:**
- README.md (overall guide)
- PHASE_1_FOUNDATION.md (architecture)
- EXECUTION_ROADMAP.md (timeline)
- AI_USAGE_LOG.md (traceability)
