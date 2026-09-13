package com.springda.devnest.flowchart;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface FlowchartRepository extends JpaRepository<FlowchartEntity, String> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "update FlowchartEntity f set f.domainId=null, f.version=f.version+1, f.updatedAt=:now"
                + " where f.ownerId=:ownerId and f.domainId=:domainId")
    int detachDomain(String ownerId, String domainId, java.time.Instant now);

    Optional<FlowchartEntity> findByIdAndOwnerId(String id, String ownerId);

    Optional<FlowchartEntity> findByOwnerIdAndCreationKey(String ownerId, String creationKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FlowchartEntity f where f.id=:id and f.ownerId=:ownerId")
    Optional<FlowchartEntity> findForUpdate(String id, String ownerId);

    @Query(
            "select new"
                + " com.springda.devnest.flowchart.FlowchartDtos$Summary(f.id,f.title,f.domainId,f.favorite,f.createdAt,f.updatedAt,f.version,f.deletedAt,f.nodeCount,f.edgeCount,substring(f.searchText,1,240))"
                + " from FlowchartEntity f where f.ownerId=:ownerId and ((:trash=true and"
                + " f.deletedAt is not null) or (:trash=false and f.deletedAt is null)) and"
                + " (lower(f.title) like :query escape '!' or lower(f.searchText) like :query"
                + " escape '!') order by f.favorite desc,f.updatedAt desc,f.id desc")
    List<FlowchartDtos.Summary> summaries(String ownerId, boolean trash, String query);
}
