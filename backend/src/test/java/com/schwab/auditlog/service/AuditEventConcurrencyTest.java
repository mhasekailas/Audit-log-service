package com.schwab.auditlog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.auditlog.dto.AuditEventResponse;
import com.schwab.auditlog.dto.ChainVerificationResponse;
import com.schwab.auditlog.dto.CreateEventRequest;
import com.schwab.auditlog.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the audit hash chain remains contiguous and tamper-free when multiple
 * writers create events concurrently (P1 "chain concurrency" gap).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditEventConcurrencyTest {

    @Autowired
    private AuditEventService auditEventService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private MockMvc mockMvc;

    private static final int THREAD_COUNT = 20;
    private static final int EVENTS_PER_THREAD = 5;
    private static final String WRITER = "test-writer";
    private static final String WRITER_PW = "writer-pass-123";

    @BeforeEach
    void clearAuditEvents() {
        auditEventRepository.deleteAll();
        auditEventRepository.flush();
    }

    @Test
    void concurrentWritersProduceContiguousSequenceAndValidChain() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode payload = objectMapper.readTree("{\"ok\":true}");

        int totalEvents = THREAD_COUNT * EVENTS_PER_THREAD;
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger failures = new AtomicInteger(0);

        for (int t = 0; t < THREAD_COUNT; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < EVENTS_PER_THREAD; i++) {
                        CreateEventRequest request = CreateEventRequest.builder()
                            .eventType("CONCURRENT_WRITE")
                            .actorId("actor-thread-" + threadId)
                            .resourceType("ACCOUNT")
                            .resourceId("acct-" + threadId + "-" + i)
                            .payload(payload)
                            .build();
                        auditEventService.createEvent(request);
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Concurrent writers did not finish in time");
        executor.shutdown();

        assertEquals(0, failures.get(), "No writer thread should fail/throw");

        List<AuditEventResponse> events = auditEventRepository.findAllByOrderBySequenceNumberAsc()
            .stream()
            .filter(e -> "CONCURRENT_WRITE".equals(e.getEventType()))
            .map(e -> AuditEventResponse.builder().sequenceNumber(e.getSequenceNumber()).build())
            .collect(Collectors.toList());

        assertEquals(totalEvents, events.size(), "Every submitted event should be persisted exactly once");

        // Sequence numbers assigned to these events must be contiguous with no gaps or duplicates.
        List<Long> sequences = events.stream().map(AuditEventResponse::getSequenceNumber).sorted().collect(Collectors.toList());
        Set<Long> distinctSequences = Set.copyOf(sequences);
        assertEquals(sequences.size(), distinctSequences.size(), "Sequence numbers must be unique (no duplicate/racing writers)");
        for (int i = 1; i < sequences.size(); i++) {
            assertEquals(sequences.get(i - 1) + 1, sequences.get(i), "Sequence numbers must be contiguous (no gaps)");
        }

        // The full hash chain must still verify cleanly after concurrent writes.
        ChainVerificationResponse verification = auditEventService.verifyChain();
        assertTrue(verification.getIsValid(), "Chain must remain valid after concurrent writes: " + verification.getFirstBreach());
    }

    @Test
    void concurrentAuthenticatedApiWritesProduceValidChain() throws Exception {
        int threadCount = 4;
        int eventsPerThread = 3;
        int totalEvents = threadCount * eventsPerThread;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger failures = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < eventsPerThread; i++) {
                        String body = "{\"eventType\":\"API_CONCURRENT_WRITE\",\"actorId\":\"test-writer\"," +
                            "\"resourceType\":\"ACCOUNT\",\"resourceId\":\"api-acct-" + threadId + "-" + i + "\"," +
                            "\"payload\":{\"ok\":true}}";
                        mockMvc.perform(post("/audit/events")
                                .with(httpBasic(WRITER, WRITER_PW))
                                .header("Idempotency-Key", "api-concurrent-" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                            .andExpect(status().isCreated());
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Concurrent API writers did not finish in time");
        executor.shutdown();

        assertEquals(0, failures.get(), "No authenticated API writer should fail/throw");

        List<Long> sequences = auditEventRepository.findAllByOrderBySequenceNumberAsc()
            .stream()
            .filter(e -> "API_CONCURRENT_WRITE".equals(e.getEventType()))
            .map(e -> e.getSequenceNumber())
            .sorted()
            .collect(Collectors.toList());

        assertEquals(totalEvents, sequences.size(), "Every authenticated API write should be persisted exactly once");
        assertEquals(sequences.size(), Set.copyOf(sequences).size(), "API write sequence numbers must be unique");
        for (int i = 1; i < sequences.size(); i++) {
            assertEquals(sequences.get(i - 1) + 1, sequences.get(i), "API write sequence numbers must be contiguous");
        }

        ChainVerificationResponse verification = auditEventService.verifyChain();
        assertTrue(verification.getIsValid(), "Chain must remain valid after concurrent authenticated API writes: " + verification.getFirstBreach());
    }

    @Test
    void failedDatabaseWriteRollsBackWithoutSequenceGapOrBrokenChain() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode payload = objectMapper.readTree("{\"ok\":true}");

        AuditEventResponse baseline = auditEventService.createEvent(CreateEventRequest.builder()
            .eventType("ROLLBACK_BASELINE")
            .actorId("rollback-actor")
            .resourceType("ACCOUNT")
            .resourceId("rollback-baseline")
            .payload(payload)
            .build());
        long countBeforeFailure = auditEventRepository.count();

        CreateEventRequest invalidRequest = CreateEventRequest.builder()
            .eventType("X".repeat(51))
            .actorId("rollback-actor")
            .resourceType("ACCOUNT")
            .resourceId("rollback-invalid")
            .payload(payload)
            .build();

        assertThrows(DataIntegrityViolationException.class, () -> auditEventService.createEvent(invalidRequest));
        assertEquals(countBeforeFailure, auditEventRepository.count(), "Failed DB write must not leave a partial audit row");

        AuditEventResponse afterRollback = auditEventService.createEvent(CreateEventRequest.builder()
            .eventType("ROLLBACK_AFTER_FAILURE")
            .actorId("rollback-actor")
            .resourceType("ACCOUNT")
            .resourceId("rollback-after")
            .payload(payload)
            .build());

        assertEquals(baseline.getSequenceNumber() + 1, afterRollback.getSequenceNumber(),
            "Rolled-back write must not consume a persisted audit sequence number");

        ChainVerificationResponse verification = auditEventService.verifyChain();
        assertTrue(verification.getIsValid(), "Chain must remain valid after a rolled-back write: " + verification.getFirstBreach());
    }
}
