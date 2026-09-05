package com.springda.devnest.log;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DevLogRepository extends JpaRepository<DevLogEntity, String> {

    List<DevLogEntity> findAllByOwnerIdAndDeletedAtIsNullOrderByPinnedDescCreatedAtDesc(String ownerId);

    List<DevLogEntity> findAllByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(String ownerId);

    Optional<DevLogEntity> findByIdAndOwnerId(String id, String ownerId);
}
