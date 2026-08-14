package com.schwab.auditlog.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HashUtilTest {
    private HashUtil hashUtil;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        hashUtil = new HashUtil(objectMapper);
    }

    @Test
    void hashStringIsDeterministicSha256Hex() {
        String hash = HashUtil.hashString("audit-event");

        assertEquals(64, hash.length());
        assertEquals(hash, HashUtil.hashString("audit-event"));
        assertNotEquals(hash, HashUtil.hashString("different-event"));
    }

    @Test
    void contentHashChangesWhenAnyEventFieldChanges() throws Exception {
        String baseline = hashUtil.computeContentHash("LOGIN", "actor", "ACCOUNT", "1",
            objectMapper.readTree("{\"result\":\"ok\"}"), "2026-08-14T10:00:00");
        String changedPayload = hashUtil.computeContentHash("LOGIN", "actor", "ACCOUNT", "1",
            objectMapper.readTree("{\"result\":\"denied\"}"), "2026-08-14T10:00:00");

        assertNotEquals(baseline, changedPayload);
    }

    @Test
    void genesisAndChainHashAreDeterministic() {
        String genesis = hashUtil.getGenesisHash();
        String chain = hashUtil.computeChainHash(genesis, "content-hash");

        assertEquals(genesis, hashUtil.getGenesisHash());
        assertEquals(chain, hashUtil.computeChainHash(genesis, "content-hash"));
        assertNotEquals(chain, hashUtil.computeChainHash("other-previous", "content-hash"));
    }
}
