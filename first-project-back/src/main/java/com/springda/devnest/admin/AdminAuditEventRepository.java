package com.springda.devnest.admin;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAuditEventRepository extends JpaRepository<AdminAuditEventEntity, String> {
    List<AdminAuditEventEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
