package com.schwab.auditlog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.schwab.auditlog.dto.BulkExportResponse;
import com.schwab.auditlog.dto.RedactionRequest;
import com.schwab.auditlog.dto.RetentionPolicyRequest;
import com.schwab.auditlog.model.AuditEvent;
import com.schwab.auditlog.model.RedactionLog;
import com.schwab.auditlog.model.RetentionPolicy;
import com.schwab.auditlog.repository.AuditEventRepository;
import com.schwab.auditlog.repository.RedactionLogRepository;
import com.schwab.auditlog.repository.RetentionPolicyRepository;
import com.schwab.auditlog.util.HashUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class RetentionRedactionService {
    private static final String REDACTED_VALUE = "[REDACTED]";

    private final AuditEventRepository eventRepository;
    private final RedactionLogRepository redactionLogRepository;
    private final RetentionPolicyRepository retentionPolicyRepository;
    private final HashUtil hashUtil;
    private final ObjectMapper objectMapper;

    public RetentionRedactionService(AuditEventRepository eventRepository,
                            RedactionLogRepository redactionLogRepository,
                            RetentionPolicyRepository retentionPolicyRepository,
                            HashUtil hashUtil,
                            ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.redactionLogRepository = redactionLogRepository;
        this.retentionPolicyRepository = retentionPolicyRepository;
        this.hashUtil = hashUtil;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RetentionPolicy upsertPolicy(RetentionPolicyRequest request) {
        RetentionPolicy policy = retentionPolicyRepository.findByResourceType(request.getResourceType())
            .orElseGet(RetentionPolicy::new);
        policy.setResourceType(request.getResourceType());
        policy.setRetentionDays(request.getRetentionDays());
        policy.setArchiveOnExpiry(request.getArchiveOnExpiry() == null || request.getArchiveOnExpiry());
        return retentionPolicyRepository.save(policy);
    }

    @Transactional
    public int archiveExpired(LocalDateTime asOf) {
        int archived = 0;
        for (RetentionPolicy policy : retentionPolicyRepository.findAll()) {
            if (!Boolean.TRUE.equals(policy.getArchiveOnExpiry())) continue;
            LocalDateTime cutoff = asOf.minusDays(policy.getRetentionDays());
            for (AuditEvent event : eventRepository.findByTimestampBeforeAndIsArchivedFalse(cutoff)) {
                if (policy.getResourceType() == null || policy.getResourceType().equals(event.getResourceType())) {
                    event.setIsArchived(true);
                    event.setArchivedAt(asOf);
                    eventRepository.save(event);
                    archived++;
                }
            }
        }
        return archived;
    }

    @Transactional
    public AuditEvent redact(Long eventId, RedactionRequest request) {
        AuditEvent event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found with ID: " + eventId));
        if (!(event.getPayload() instanceof ObjectNode)) {
            throw new IllegalArgumentException("Only object payloads support structured redaction");
        }

        ObjectNode redactedPayload = ((ObjectNode) event.getPayload()).deepCopy();
        int redactedFields = 0;
        for (String fieldPath : request.getFieldPaths()) {
            String[] segments = normalizePath(fieldPath);
            JsonNode original = findValue(redactedPayload, segments);
            if (original == null || original.isMissingNode()) {
                throw new IllegalArgumentException("Payload field not found: " + fieldPath);
            }
            setValue(redactedPayload, segments, objectMapper.getNodeFactory().textNode(REDACTED_VALUE));
            redactionLogRepository.save(RedactionLog.builder()
                .auditEventId(eventId)
                .fieldPath(fieldPath)
                .redactionReason(request.getReason())
                .redactionHash(HashUtil.hashString(original.toString()))
                .build());
            redactedFields++;
        }
        if (redactedFields == 0) throw new IllegalArgumentException("No fields were redacted");

        event.setPayload(redactedPayload);
        eventRepository.save(event);
        rebuildChain();
        return eventRepository.findById(eventId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public BulkExportResponse export(String actorId, String resourceId) {
        if ((actorId == null) == (resourceId == null)) {
            throw new IllegalArgumentException("Provide exactly one of actorId or resourceId");
        }
        List<AuditEvent> records = new ArrayList<>(actorId != null
            ? eventRepository.findByActorIdOrderBySequenceNumberAsc(actorId)
            : eventRepository.findByResourceIdOrderBySequenceNumberAsc(resourceId));
        records.sort(Comparator.comparing(AuditEvent::getSequenceNumber));

        List<AuditEvent> all = eventRepository.findAllByOrderBySequenceNumberAsc();
        String predecessor = hashUtil.getGenesisHash();
        if (!records.isEmpty()) {
            Long firstSequence = records.get(0).getSequenceNumber();
            predecessor = all.stream()
                .filter(event -> event.getSequenceNumber() < firstSequence)
                .max(Comparator.comparing(AuditEvent::getSequenceNumber))
                .map(AuditEvent::getChainHash)
                .orElse(hashUtil.getGenesisHash());
        }

        String exportType = actorId != null ? "ACTOR_ID" : "RESOURCE_ID";
        String exportValue = actorId != null ? actorId : resourceId;
        List<BulkExportResponse.ExportRecord> exportRecords = new ArrayList<>();
        String previous = predecessor;
        for (AuditEvent event : records) {
            exportRecords.add(BulkExportResponse.ExportRecord.builder()
                .id(event.getId())
                .sequenceNumber(event.getSequenceNumber())
                .eventType(event.getEventType())
                .actorId(event.getActorId())
                .resourceType(event.getResourceType())
                .resourceId(event.getResourceId())
                .payload(event.getPayload())
                .timestamp(event.getTimestamp())
                .contentHash(event.getContentHash())
                .chainHash(event.getChainHash())
                .previousChainHash(previous)
                .build());
            previous = event.getChainHash();
        }
        return BulkExportResponse.builder()
            .exportType(exportType)
            .exportValue(exportValue)
            .algorithm("SHA-256; contentHash=event fields; chainHash=previousChainHash+contentHash")
            .genesisHash(hashUtil.getGenesisHash())
            .predecessorChainHash(predecessor)
            .exportedAt(LocalDateTime.now())
            .records(exportRecords)
            .build();
    }

    private void rebuildChain() {
        String previous = hashUtil.getGenesisHash();
        for (AuditEvent current : eventRepository.findAllByOrderBySequenceNumberAsc()) {
            current.setContentHash(hashUtil.computeContentHash(
                current.getEventType(), current.getActorId(), current.getResourceType(),
                current.getResourceId(), current.getPayload(), current.getTimestamp().toString()));
            current.setChainHash(hashUtil.computeChainHash(previous, current.getContentHash()));
            previous = current.getChainHash();
            eventRepository.save(current);
        }
    }

    private String[] normalizePath(String path) {
        return path.replaceFirst("^/", "").split("[/.]");
    }

    private JsonNode findValue(ObjectNode root, String[] segments) {
        JsonNode current = root;
        for (String segment : segments) {
            if (current == null || !current.isObject()) return null;
            current = current.get(segment);
        }
        return current;
    }

    private void setValue(ObjectNode root, String[] segments, JsonNode value) {
        ObjectNode current = root;
        for (int i = 0; i < segments.length - 1; i++) {
            JsonNode child = current.get(segments[i]);
            if (!(child instanceof ObjectNode)) {
                throw new IllegalArgumentException("Nested payload path is not an object: " + String.join(".", segments));
            }
            current = (ObjectNode) child;
        }
        current.set(segments[segments.length - 1], value);
    }
}
