# Attestation

**Full Name:** Kailas Mhase  
**Email Address:** mhasekailas@gmail.com  
**Assignment Title:** Audit log service  
**Start Date:** 08/14/2026  
**Submission Date:** 08/18/2026  

I, Kailas Mhase, attest that this submission is my own individual work, completed on my own machine and accounts, and that it honestly reflects my development process and use of AI.

## Revision Identity

| Field | Value |
|---|---|
| Repository URL | https://github.com/mhasekailas/Audit-log-service.git |
| Branch | main |
| Commit SHA | 010746301ff6edc09fd9e8212659f4f739da36a7 |
| Working tree state | Dirty - includes uncommitted security, migration, test, CI, and config hardening changes listed by `git status --short` on 2026-08-19. No commit or push was performed after these changes. |

## Claim-to-Evidence Matrix

| Claim | Evidence | How to reproduce |
|---|---|---|
| Role-based access control (reader/writer/admin) is enforced on audit endpoints | `backend/src/main/java/com/schwab/auditlog/config/SecurityConfig.java`; method guards in `AuditLogController`; `AuditLogControllerSecurityTest` | `mvn test -Dtest=AuditLogControllerSecurityTest` |
| Sensitive APIs are not publicly reachable | `AuditLogControllerSecurityTest#sensitiveAuditApisRequireAuthentication` covers audit query, write, redaction, retention, export, and compliance endpoints | `mvn test -Dtest=AuditLogControllerSecurityTest#sensitiveAuditApisRequireAuthentication` |
| Resource ownership is enforced for non-admin actors | `AuditEventService#assertActorIdentityMatchesPrincipal`, `assertEventVisibleToPrincipal`; security tests for cross-actor create/query/export denial | `mvn test -Dtest=AuditLogControllerSecurityTest` |
| Replay/duplicate-submission protection is mandatory for audit writes | `AuditLogController#createEvent` requires `Idempotency-Key`; `AuditEventService#createEvent`; `createEventWithoutIdempotencyKeyIsRejected`; `duplicateIdempotencyKeyReturnsOriginalEventInsteadOfDuplicate` | `mvn test -Dtest=AuditLogControllerSecurityTest#createEventWithoutIdempotencyKeyIsRejected+duplicateIdempotencyKeyReturnsOriginalEventInsteadOfDuplicate+writerCanCreateEvents` |
| Per-client rate limiting returns 429 on abuse | `RateLimitingFilter`; `RateLimitingFilterTest#allowsRequestsUnderTheLimitThenBlocksWith429` | `mvn test -Dtest=RateLimitingFilterTest` |
| Oversized request bodies are rejected before parsing/DB work | `RequestSizeLimitFilter`; `AuditLogControllerValidationTest#oversizedPayloadReturns413` | `mvn test -Dtest=AuditLogControllerValidationTest#oversizedPayloadReturns413` |
| TLS is enforced for protected APIs before deployment | `HttpsEnforcementFilter`; `audit.security.require-https=${AUDIT_REQUIRE_HTTPS:true}`; `HttpsEnforcementFilterTest` | `mvn test -Dtest=HttpsEnforcementFilterTest` |
| Malformed JSON, missing fields, and bad path params return controlled 400 responses | `GlobalExceptionHandler`; `AuditLogControllerValidationTest` | `mvn test -Dtest=AuditLogControllerValidationTest` |
| Server/DB errors do not leak exception or SQL detail to clients | `GlobalExceptionHandler#handleDataAccess`; `databaseFailureDuringQueryReturnsGenericServerErrorWithoutLeakingDetails` | `mvn test -Dtest=AuditLogControllerValidationTest#databaseFailureDuringQueryReturnsGenericServerErrorWithoutLeakingDetails` |
| Concurrent writers cannot race the hash-chain tail | `AuditEventService#createEvent` uses JVM serialization plus `ChainLockRepository#lockChainTailForUpdate`; `AuditEventConcurrencyTest` verifies unique/contiguous sequences and valid chain under parallel service/API writes | `mvn test -Dtest=AuditEventConcurrencyTest` |
| Chain writes fail closed if the DB lock row is missing | `AuditEventService#createEvent`; `AuditEventServiceTest#createEventFailsClosedIfChainLockRowIsMissing` | `mvn test -Dtest=AuditEventServiceTest#createEventFailsClosedIfChainLockRowIsMissing` |
| Chain verification detects content tampering, chain-hash tampering, and deleted-middle records | `AuditEventServiceTest` (`verifyChainDetectsFirstRecordContentTampering`, `verifyChainDetectsLaterChainBreak`, `verifyChainDetectsDeletedMiddleRecord`) | `mvn test -Dtest=AuditEventServiceTest` |
| DB schema is migration-managed; Hibernate validates only | `backend/src/main/resources/db/migration/V1__init_audit_schema.sql`; `backend/pom.xml` Flyway dependency; `spring.jpa.hibernate.ddl-auto=validate`; `spring.flyway.enabled=true` | Inspect files; run `mvn test` or start the app against PostgreSQL |
| SQL logging is disabled for production data safety | `spring.jpa.show-sql=false`; `logging.level.org.hibernate.SQL=WARN` in `backend/src/main/resources/application.properties` | Inspect file directly |
| Secrets are sourced from environment/config import, not source-controlled literals | `application.properties` uses `${AUDIT_DB_USERNAME}`, `${AUDIT_DB_PASSWORD}`, role credential env vars, and optional `${SPRING_CONFIG_IMPORT}` | Inspect `application.properties` and `SecurityConfig#userDetailsService` |
| Surefire and JaCoCo artifacts are generated and uploaded in CI | `backend/pom.xml` Surefire/JaCoCo plugins; `.github/workflows/backend-ci.yml` uploads `backend-surefire-reports` and `backend-jacoco-report` | `mvn test`; GitHub Actions workflow on push/PR |

## Captured Validation Results

Captured from `backend/target/surefire-reports` on 2026-08-19 after the local hardening changes:

| Test class / command scope | Result |
|---|---|
| `HttpsEnforcementFilterTest` | Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 |
| `RateLimitingFilterTest` | Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 |
| `AuditLogControllerSecurityTest` | Tests run: 33, Failures: 0, Errors: 0, Skipped: 0 in the latest full-class report; latest single-method context check also passed |
| `AuditLogControllerValidationTest` | Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 |
| `AuditScenarioEndToEndTest` | Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 |
| `AuditEventConcurrencyTest` | Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 |
| `AuditEventServiceTest` | Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 |
| `ComplianceReportServiceTest` | Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 |
| `RetentionRedactionServiceTest` | Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 |
| `ScenarioBServiceTest` | Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 |
| `HashUtilTest` | Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 |

Additional focused validations captured during hardening:

- `mvn test -Dtest=AuditLogControllerSecurityTest#createEventWithoutIdempotencyKeyIsRejected+duplicateIdempotencyKeyReturnsOriginalEventInsteadOfDuplicate+writerCanCreateEvents`: Tests run: 3, Failures: 0, Errors: 0.
- `mvn test -Dtest=AuditEventServiceTest,AuditEventConcurrencyTest`: `AuditEventServiceTest` 9/0/0 and `AuditEventConcurrencyTest` 3/0/0.
- `mvn test -Dtest=AuditLogControllerSecurityTest#healthEndpointIsPublic`: Tests run: 1, Failures: 0, Errors: 0.

Artifacts generated locally:

- Surefire: `backend/target/surefire-reports/*.txt` and `backend/target/surefire-reports/TEST-*.xml`
- JaCoCo: `backend/target/site/jacoco/index.html`, `backend/target/site/jacoco/jacoco.csv`, `backend/target/jacoco.exec`

Working-tree note: validation was performed against the local dirty working tree after applying the hardening changes. The exact committed base is recorded above; no commit or push was performed after these edits.
