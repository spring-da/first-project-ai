package com.springda.devnest.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeDomainRepository extends JpaRepository<KnowledgeDomainEntity, String> {

    List<KnowledgeDomainEntity> findAllByOwnerIdOrderBySortOrderAscCreatedAtAsc(String ownerId);

    Optional<KnowledgeDomainEntity> findByIdAndOwnerId(String id, String ownerId);

    boolean existsByIdAndOwnerId(String id, String ownerId);

    boolean existsByOwnerIdAndNameIgnoreCase(String ownerId, String name);

    boolean existsByOwnerIdAndNameIgnoreCaseAndIdNot(String ownerId, String name, String id);
}
