package com.schwab.auditlog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.auditlog.dto.AuditEventResponse;
import com.schwab.auditlog.dto.ChainVerificationResponse;
import com.schwab.auditlog.dto.CreateEventRequest;
import com.schwab.auditlog.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the audit hash chain remains contiguous and tamper-free when multiple
 * writers create events concurrently (P1 "chain concurrency" gap).
 */
@SpringBootTest
class AuditEventConcurrencyTest {

    @Autowired
    private AuditEventService auditEventService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    private static final int THREAD_COUNT = 20;
    private static final int EVENTS_PER_THREAD = 5;

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
        assertTrue(verification.getIsValid(), "Chain must remain valid after concurrent writes");
    }
}
