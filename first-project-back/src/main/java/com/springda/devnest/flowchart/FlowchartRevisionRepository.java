package com.springda.devnest.flowchart;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface FlowchartRevisionRepository
        extends JpaRepository<FlowchartRevisionEntity, String> {
    Optional<FlowchartRevisionEntity> findFirstByDocumentIdAndOwnerIdOrderByDocumentVersionDesc(
            String documentId, String ownerId);

    Optional<FlowchartRevisionEntity> findByIdAndDocumentIdAndOwnerId(
            String id, String documentId, String ownerId);

    List<FlowchartRevisionEntity> findByDocumentIdAndOwnerIdOrderByDocumentVersionDesc(
            String documentId, String ownerId, Pageable pageable);

    @Query(
            "select new"
                + " com.springda.devnest.flowchart.FlowchartDtos$RevisionSummary(r.id,r.documentVersion,r.title,r.action,r.createdAt)"
                + " from FlowchartRevisionEntity r where r.documentId=:documentId and"
                + " r.ownerId=:ownerId order by r.documentVersion desc,r.id desc")
    List<FlowchartDtos.RevisionSummary> summaries(
            String documentId, String ownerId, Pageable pageable);

    void deleteByDocumentIdAndOwnerId(String documentId, String ownerId);
}
