package com.springda.devnest.announcement;

import com.springda.devnest.admin.AdminAuditAction;
import com.springda.devnest.admin.AdminAuditService;
import com.springda.devnest.common.ForbiddenException;
import com.springda.devnest.common.NotFoundException;
import com.springda.devnest.user.UserEntity;
import com.springda.devnest.user.UserRepository;
import com.springda.devnest.user.UserRole;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AnnouncementService {
    private static final int MAX_UNREAD = 20;
    private static final int MAX_LIST = 50;

    private final SystemAnnouncementRepository announcements;
    private final AnnouncementReadRepository reads;
    private final UserRepository users;
    private final AdminAuditService audit;

    public AnnouncementService(
            SystemAnnouncementRepository announcements,
            AnnouncementReadRepository reads,
            UserRepository users,
            AdminAuditService audit
    ) {
        this.announcements = announcements;
        this.reads = reads;
        this.users = users;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AnnouncementDtos.Response> unread(String readerId) {
        var reader = requireAccount(readerId);
        if (reader.getRole() == UserRole.ADMIN) return List.of();
        return announcements.findUnread(readerId, PageRequest.of(0, MAX_UNREAD)).stream()
                .map(entity -> response(entity, readerId)).toList();
    }

    @Transactional(readOnly = true)
    public List<AnnouncementDtos.Response> active(String readerId, int requestedLimit) {
        requireAccount(readerId);
        var limit = Math.max(1, Math.min(requestedLimit, MAX_LIST));
        return announcements.findByActiveTrueOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream()
                .map(entity -> response(entity, readerId)).toList();
    }

    @Transactional(readOnly = true)
    public List<AnnouncementDtos.Response> adminList(String adminId, int requestedLimit) {
        requireAdmin(adminId);
        var limit = Math.max(1, Math.min(requestedLimit, MAX_LIST));
        return announcements.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream()
                .map(entity -> response(entity, adminId)).toList();
    }

    @Transactional
    public void markRead(String readerId, String announcementId) {
        var reader = requireAccount(readerId);
        if (reader.getRole() == UserRole.ADMIN) return;
        var announcement = announcements.findById(announcementId)
                .orElseThrow(() -> new NotFoundException("系统公告", announcementId));
        if (!reads.existsByAnnouncementIdAndReaderId(announcement.getId(), readerId)) {
            reads.save(new AnnouncementReadEntity(announcement.getId(), readerId));
        }
    }

    @Transactional
    public AnnouncementDtos.Response publish(String adminId, AnnouncementDtos.PublishRequest request) {
        var admin = requireAdmin(adminId);
        var announcement = announcements.save(new SystemAnnouncementEntity(
                adminId, request.title().strip(), request.content().strip()));
        audit.record(adminId, admin.getEmail(), announcement.getId(), announcement.getTitle(),
                AdminAuditAction.ANNOUNCEMENT_PUBLISHED, "announcements", "POST",
                "/api/v1/admin/announcements", 201, true);
        return response(announcement, adminId);
    }

    @Transactional
    public void archive(String adminId, String announcementId) {
        var admin = requireAdmin(adminId);
        var announcement = announcements.findById(announcementId)
                .orElseThrow(() -> new NotFoundException("系统公告", announcementId));
        announcement.archive();
        audit.record(adminId, admin.getEmail(), announcement.getId(), announcement.getTitle(),
                AdminAuditAction.ANNOUNCEMENT_ARCHIVED, "announcements", "DELETE",
                "/api/v1/admin/announcements/" + announcementId, 204, true);
    }

    private AnnouncementDtos.Response response(SystemAnnouncementEntity entity, String readerId) {
        var publisher = users.findById(entity.getPublishedBy()).orElse(null);
        return new AnnouncementDtos.Response(
                entity.getId(), entity.getTitle(), entity.getContent(),
                publisher == null ? "管理员" : publisher.getDisplayName(), entity.isActive(),
                reads.existsByAnnouncementIdAndReaderId(entity.getId(), readerId),
                reads.countByAnnouncementId(entity.getId()), entity.getCreatedAt());
    }

    private UserEntity requireAccount(String userId) {
        var user = users.findById(userId).orElseThrow(() -> new ForbiddenException("账号不可用"));
        if (!user.isEnabled()) throw new ForbiddenException("账号不可用");
        return user;
    }

    private UserEntity requireAdmin(String userId) {
        var user = requireAccount(userId);
        if (user.getRole() != UserRole.ADMIN) throw new ForbiddenException("需要管理员权限");
        return user;
    }
}
