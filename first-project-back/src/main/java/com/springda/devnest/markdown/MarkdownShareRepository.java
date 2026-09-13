package com.springda.devnest.markdown;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MarkdownShareRepository extends JpaRepository<MarkdownShareEntity, String> {
    List<MarkdownShareEntity> findAllByDocumentIdAndOwnerIdOrderByCreatedAtDesc(String documentId, String ownerId);
    Optional<MarkdownShareEntity> findByIdAndDocumentIdAndOwnerId(String id, String documentId, String ownerId);
    Optional<MarkdownShareEntity> findByTokenDigest(String tokenDigest);
    long countByDocumentIdAndOwnerIdAndRevokedAtIsNullAndExpiresAtAfterAndResourceGeneration(
            String documentId,
            String ownerId,
            Instant now,
            long resourceGeneration
    );
}
