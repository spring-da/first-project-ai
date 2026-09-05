package com.springda.devnest.markdown;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MarkdownDocumentRepository extends JpaRepository<MarkdownDocumentEntity, String> {

    @Query("""
            select document.id as id,
                   document.title as title,
                   document.fileName as fileName,
                   substring(document.content, 1, :excerptLength) as excerpt,
                   length(document.content) as contentLength,
                   document.favorite as favorite,
                   document.domainId as domainId,
                   document.createdAt as createdAt,
                   document.updatedAt as updatedAt,
                   document.version as version,
                   document.deletedAt as deletedAt
              from MarkdownDocumentEntity document
             where document.ownerId = :ownerId and document.deletedAt is null
             order by document.favorite desc, document.updatedAt desc
            """)
    List<SummaryProjection> findSummariesByOwnerId(
            @Param("ownerId") String ownerId,
            @Param("excerptLength") int excerptLength
    );

    @Query("""
            select document.id as id, document.title as title, document.fileName as fileName,
                   substring(document.content, 1, 180) as excerpt, length(document.content) as contentLength,
                   document.favorite as favorite, document.domainId as domainId,
                   document.createdAt as createdAt, document.updatedAt as updatedAt,
                   document.version as version, document.deletedAt as deletedAt
              from MarkdownDocumentEntity document
             where document.ownerId = :ownerId and document.deletedAt is not null
             order by document.deletedAt desc
            """)
    List<SummaryProjection> findTrashByOwnerId(@Param("ownerId") String ownerId);

    List<MarkdownDocumentEntity> findAllByOwnerIdAndDeletedAtIsNullOrderByFavoriteDescUpdatedAtDesc(String ownerId);

    Optional<MarkdownDocumentEntity> findByIdAndOwnerId(String id, String ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select document from MarkdownDocumentEntity document where document.id = :id and document.ownerId = :ownerId")
    Optional<MarkdownDocumentEntity> findForUpdate(@Param("id") String id, @Param("ownerId") String ownerId);

    List<MarkdownDocumentEntity> findAllByOwnerIdAndIdInAndDeletedAtIsNull(String ownerId, Collection<String> ids);

    long countByOwnerIdAndDeletedAtIsNull(String ownerId);

    long countByOwnerIdAndIdInAndDeletedAtIsNull(String ownerId, Collection<String> ids);

    @Query(value = """
            SELECT COALESCE(SUM(OCTET_LENGTH(content)), 0)
              FROM markdown_documents
             WHERE owner_id = :ownerId AND deleted_at IS NULL
            """, nativeQuery = true)
    long sumContentOctetsByOwnerId(@Param("ownerId") String ownerId);

    @Query(value = """
            SELECT COALESCE(SUM(OCTET_LENGTH(content)), 0)
              FROM markdown_documents
             WHERE owner_id = :ownerId AND deleted_at IS NULL
               AND id IN (:ids)
            """, nativeQuery = true)
    long sumContentOctetsByOwnerIdAndIdIn(
            @Param("ownerId") String ownerId,
            @Param("ids") Collection<String> ids
    );

    interface SummaryProjection {
        String getId();
        String getTitle();
        String getFileName();
        String getExcerpt();
        Integer getContentLength();
        Boolean getFavorite();
        String getDomainId();
        Instant getCreatedAt();
        Instant getUpdatedAt();
        long getVersion();
        Instant getDeletedAt();
    }
}
