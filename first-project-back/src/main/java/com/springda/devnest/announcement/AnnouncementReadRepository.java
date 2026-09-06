package com.springda.devnest.announcement;

import org.springframework.data.jpa.repository.JpaRepository;

interface AnnouncementReadRepository extends JpaRepository<AnnouncementReadEntity, String> {
    boolean existsByAnnouncementIdAndReaderId(String announcementId, String readerId);
    long countByAnnouncementId(String announcementId);
}
