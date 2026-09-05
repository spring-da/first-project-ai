package com.springda.devnest.admin;

import com.springda.devnest.common.ForbiddenException;
import com.springda.devnest.user.UserRepository;
import com.springda.devnest.user.UserRole;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminAuditService {
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminAuditEventRepository events;
    private final UserRepository users;

    public AdminAuditService(AdminAuditEventRepository events, UserRepository users) {
        this.events = events;
        this.users = users;
    }

    @Transactional
    public void record(
            String actorId,
            String actorEmail,
            String targetId,
            String targetLabel,
            AdminAuditAction action,
            String resourceType,
            String httpMethod,
            String requestPath,
            int responseStatus,
            boolean success
    ) {
        events.save(new AdminAuditEventEntity(
                actorId,
                limit(actorEmail, 190),
                limit(targetId, 190),
                limit(targetLabel, 190),
                action,
                limit(resourceType, 50),
                limit(httpMethod, 10),
                limit(requestPath, 500),
                responseStatus,
                success));
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.AuditEventResponse> list(String adminId, int requestedLimit) {
        var admin = users.findById(adminId)
                .orElseThrow(() -> new ForbiddenException("需要管理员权限"));
        if (!admin.isEnabled() || admin.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("需要管理员权限");
        }
        var limit = Math.max(1, Math.min(requestedLimit, MAX_PAGE_SIZE));
        return events.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream()
                .map(AdminDtos.AuditEventResponse::from)
                .toList();
    }

    private String limit(String value, int maxLength) {
        if (value == null) return null;
        var normalized = value.strip();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
