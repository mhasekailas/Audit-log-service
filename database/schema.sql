-- Audit Log Service Database Schema
-- PostgreSQL 12+

-- Create audit_events table with hash chain support
CREATE TABLE audit_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    content_hash VARCHAR(64) NOT NULL,
    chain_hash VARCHAR(64) NOT NULL,
    sequence_number BIGINT NOT NULL UNIQUE,
    idempotency_key VARCHAR(255) UNIQUE,
    is_archived BOOLEAN DEFAULT FALSE,
    archived_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for query performance
CREATE INDEX idx_actor_id ON audit_events(actor_id);
CREATE INDEX idx_event_type ON audit_events(event_type);
CREATE INDEX idx_resource ON audit_events(resource_type, resource_id);
CREATE INDEX idx_timestamp ON audit_events(timestamp);
CREATE INDEX idx_sequence ON audit_events(sequence_number);
CREATE INDEX idx_is_archived ON audit_events(is_archived);

-- Create redaction_log table for tracking field redactions
CREATE TABLE redaction_log (
    id BIGSERIAL PRIMARY KEY,
    audit_event_id BIGINT NOT NULL REFERENCES audit_events(id) ON DELETE CASCADE,
    field_path VARCHAR(255) NOT NULL,
    redacted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    redaction_reason VARCHAR(500),
    redaction_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_redaction_event ON redaction_log(audit_event_id);

-- Create bulk_exports table for export tracking
CREATE TABLE bulk_exports (
    id BIGSERIAL PRIMARY KEY,
    export_type VARCHAR(50) NOT NULL, -- 'ACTOR_ID' or 'RESOURCE_ID'
    export_value VARCHAR(255) NOT NULL,
    from_record_id BIGINT NOT NULL,
    to_record_id BIGINT NOT NULL,
    total_records INT NOT NULL,
    export_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bulk_exports_value ON bulk_exports(export_type, export_value);

-- Create retention_policies table
CREATE TABLE retention_policies (
    id BIGSERIAL PRIMARY KEY,
    resource_type VARCHAR(100),
    retention_days INT NOT NULL,
    archive_on_expiry BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create compliance_audit_access table (Scenario C)
CREATE TABLE compliance_audit_access (
    id BIGSERIAL PRIMARY KEY,
    audit_event_id BIGINT NOT NULL REFERENCES audit_events(id) ON DELETE CASCADE,
    access_type VARCHAR(50) NOT NULL, -- READ, WRITE, DELETE, EXPORT, etc.
    user_role VARCHAR(100),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    access_result VARCHAR(50), -- SUCCESS, DENIED, etc.
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_compliance_event ON compliance_audit_access(audit_event_id);
CREATE INDEX idx_compliance_access_type ON compliance_audit_access(access_type);

-- Singleton row locked (SELECT ... FOR UPDATE) by every writer to serialize chain-tail
-- sequence/hash generation at the database level, even across multiple app instances.
CREATE TABLE chain_lock (
    id BIGINT PRIMARY KEY
);
INSERT INTO chain_lock (id) VALUES (1) ON CONFLICT DO NOTHING;

-- Create a view for chain verification
CREATE OR REPLACE VIEW chain_verification_view AS
SELECT 
    id,
    sequence_number,
    event_type,
    actor_id,
    resource_type,
    resource_id,
    timestamp,
    content_hash,
    chain_hash,
    LAG(sequence_number) OVER (ORDER BY sequence_number) as prev_sequence,
    LAG(chain_hash) OVER (ORDER BY sequence_number) as prev_chain_hash
FROM audit_events
WHERE is_archived = FALSE
ORDER BY sequence_number;
