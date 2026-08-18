package com.schwab.auditlog.config;

import com.schwab.auditlog.model.ChainLock;
import com.schwab.auditlog.repository.ChainLockRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures the singleton chain-tail lock row (id=1) exists before any request can race to
 * create it; schema.sql seeds it for real deployments, this covers test/dev profiles that
 * create the schema from JPA entities (ddl-auto=create-drop) without running schema.sql.
 */
@Component
public class ChainLockInitializer implements ApplicationRunner {

    private final ChainLockRepository chainLockRepository;

    public ChainLockInitializer(ChainLockRepository chainLockRepository) {
        this.chainLockRepository = chainLockRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (chainLockRepository.findById(1L).isEmpty()) {
            chainLockRepository.save(new ChainLock(1L));
        }
    }
}
