package com.springda.devnest.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<ProjectEntity, String> {

    List<ProjectEntity> findAllByOwnerIdOrderByUpdatedAtDesc(String ownerId);

    Optional<ProjectEntity> findByIdAndOwnerId(String id, String ownerId);
}
