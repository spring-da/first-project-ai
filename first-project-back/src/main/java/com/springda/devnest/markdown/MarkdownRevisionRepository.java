package com.springda.devnest.markdown;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MarkdownRevisionRepository extends JpaRepository<MarkdownRevisionEntity, String> {
    List<Summary> findByDocumentIdAndOwnerIdOrderByDocumentVersionDesc(String documentId, String ownerId, Pageable pageable);
    Optional<MarkdownRevisionEntity> findByIdAndDocumentIdAndOwnerId(String id, String documentId, String ownerId);
    @Modifying
    @Query("delete from MarkdownRevisionEntity revision where revision.documentId = :documentId and revision.ownerId = :ownerId")
    void deleteByDocumentIdAndOwnerId(@Param("documentId") String documentId, @Param("ownerId") String ownerId);

    interface Summary {
        String getId();
        long getDocumentVersion();
        String getAction();
        String getTitle();
        String getFileName();
        Instant getCreatedAt();
    }
}
