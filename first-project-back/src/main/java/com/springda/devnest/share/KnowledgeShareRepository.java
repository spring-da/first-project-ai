package com.springda.devnest.share;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface KnowledgeShareRepository extends JpaRepository<KnowledgeShareEntity, String> {
    List<KnowledgeShareEntity> findAllByResourceTypeAndResourceIdAndOwnerIdOrderByCreatedAtDesc(
            KnowledgeResourceType resourceType, String resourceId, String ownerId);

    Optional<KnowledgeShareEntity> findByIdAndResourceTypeAndResourceIdAndOwnerId(
            String id, KnowledgeResourceType resourceType, String resourceId, String ownerId);

    Optional<KnowledgeShareEntity> findByTokenDigest(String tokenDigest);

    long countByResourceTypeAndResourceIdAndOwnerIdAndRevokedAtIsNullAndExpiresAtAfterAndResourceGeneration(
            KnowledgeResourceType resourceType, String resourceId, String ownerId, Instant now, long resourceGeneration);

    void deleteAllByResourceTypeAndResourceIdAndOwnerId(
            KnowledgeResourceType resourceType, String resourceId, String ownerId);

    void deleteAllByOwnerId(String ownerId);
}
