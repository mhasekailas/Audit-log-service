package com.schwab.auditlog.repository;

import com.schwab.auditlog.model.RetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, Long> {
    Optional<RetentionPolicy> findByResourceType(String resourceType);
}
