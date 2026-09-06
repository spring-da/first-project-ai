package com.springda.devnest.announcement;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface SystemAnnouncementRepository extends JpaRepository<SystemAnnouncementEntity, String> {
    List<SystemAnnouncementEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<SystemAnnouncementEntity> findByActiveTrueOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
            select announcement from SystemAnnouncementEntity announcement
            where announcement.active = true and not exists (
                select read.id from AnnouncementReadEntity read
                where read.announcementId = announcement.id and read.readerId = :readerId
            )
            order by announcement.createdAt asc, announcement.id asc
            """)
    List<SystemAnnouncementEntity> findUnread(@Param("readerId") String readerId, Pageable pageable);
}
