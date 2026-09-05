package com.springda.devnest.admin;

import com.springda.devnest.config.WorkspaceOwnerResolver;
import com.springda.devnest.user.UserRepository;
import com.springda.devnest.user.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
public class AdminWorkspaceAuditInterceptor implements HandlerInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminWorkspaceAuditInterceptor.class);
    private static final String ATTRIBUTE = AdminWorkspaceAuditInterceptor.class.getName() + ".context";
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> WORKSPACE_RESOURCES = Set.of(
            "tasks", "projects", "domains", "knowledge-items", "markdown-documents",
            "markdown-images", "snippets", "logs", "profile");

    private final UserRepository users;
    private final AdminAuditService audit;

    public AdminWorkspaceAuditInterceptor(UserRepository users, AdminAuditService audit) {
        this.users = users;
        this.audit = audit;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var targetId = request.getHeader(WorkspaceOwnerResolver.HEADER);
        var resourceType = resourceType(request.getRequestURI());
        if (!WRITE_METHODS.contains(request.getMethod()) || targetId == null
                || request.getRequestURI().equals("/api/v1/markdown-documents/export")
                || !WORKSPACE_RESOURCES.contains(resourceType)) {
            return true;
        }
        if (!(request.getUserPrincipal() instanceof JwtAuthenticationToken authentication)) {
            return true;
        }
        var actor = users.findById(authentication.getToken().getSubject()).orElse(null);
        if (actor == null || !actor.isEnabled() || actor.getRole() != UserRole.ADMIN) {
            return true;
        }
        var target = users.findById(targetId).orElse(null);
        request.setAttribute(ATTRIBUTE, new AuditContext(
                actor.getId(), actor.getEmail(), targetId,
                target == null ? null : target.getEmail(), resourceType));
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        if (!(request.getAttribute(ATTRIBUTE) instanceof AuditContext context)) return;
        try {
            audit.record(
                    context.actorId(), context.actorEmail(), context.targetId(), context.targetLabel(),
                    AdminAuditAction.MEMBER_WORKSPACE_WRITE, context.resourceType(), request.getMethod(),
                    request.getRequestURI(), response.getStatus(), exception == null && response.getStatus() < 400);
        } catch (RuntimeException auditFailure) {
            LOGGER.warn("Could not persist administrator workspace audit event", auditFailure);
        }
    }

    private String resourceType(String requestPath) {
        var relative = requestPath.startsWith("/api/v1/") ? requestPath.substring(8) : requestPath;
        var slash = relative.indexOf('/');
        return slash < 0 ? relative : relative.substring(0, slash);
    }

    private record AuditContext(
            String actorId,
            String actorEmail,
            String targetId,
            String targetLabel,
            String resourceType
    ) {
    }
}
