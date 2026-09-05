package com.springda.devnest.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<TaskEntity, String> {

    List<TaskEntity> findAllByOwnerIdOrderBySortOrderAscCreatedAtAsc(String ownerId);

    Optional<TaskEntity> findByIdAndOwnerId(String id, String ownerId);
}
