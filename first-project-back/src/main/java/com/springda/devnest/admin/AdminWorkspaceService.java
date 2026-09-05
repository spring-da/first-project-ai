package com.springda.devnest.admin;

import com.springda.devnest.common.ForbiddenException;
import com.springda.devnest.common.NotFoundException;
import com.springda.devnest.knowledge.KnowledgeDomainService;
import com.springda.devnest.image.MarkdownImageService;
import com.springda.devnest.log.DevLogService;
import com.springda.devnest.markdown.MarkdownDocumentDtos;
import com.springda.devnest.markdown.MarkdownDocumentService;
import com.springda.devnest.profile.ProfileService;
import com.springda.devnest.project.ProjectService;
import com.springda.devnest.snippet.SnippetService;
import com.springda.devnest.task.TaskService;
import com.springda.devnest.user.UserEntity;
import com.springda.devnest.user.UserRepository;
import com.springda.devnest.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminWorkspaceService {

    private final UserRepository users;
    private final ProfileService profiles;
    private final KnowledgeDomainService domains;
    private final TaskService tasks;
    private final ProjectService projects;
    private final MarkdownDocumentService markdownDocuments;
    private final MarkdownImageService markdownImages;
    private final SnippetService snippets;
    private final DevLogService logs;

    public AdminWorkspaceService(
            UserRepository users,
            ProfileService profiles,
            KnowledgeDomainService domains,
            TaskService tasks,
            ProjectService projects,
            MarkdownDocumentService markdownDocuments,
            MarkdownImageService markdownImages,
            SnippetService snippets,
            DevLogService logs
    ) {
        this.users = users;
        this.profiles = profiles;
        this.domains = domains;
        this.tasks = tasks;
        this.projects = projects;
        this.markdownDocuments = markdownDocuments;
        this.markdownImages = markdownImages;
        this.snippets = snippets;
        this.logs = logs;
    }

    @Transactional(readOnly = true)
    public AdminWorkspaceDtos.Snapshot snapshot(String adminId, String userId) {
        requireAdmin(adminId);
        var user = targetUser(userId);
        return new AdminWorkspaceDtos.Snapshot(
                account(user),
                profiles.get(user.getId()),
                domains.list(user.getId()),
                tasks.list(user.getId()),
                projects.list(user.getId()),
                markdownDocuments.list(user.getId()),
                snippets.list(user.getId()),
                logs.list(user.getId()));
    }

    @Transactional(readOnly = true)
    public MarkdownDocumentDtos.Response markdownDocument(String adminId, String userId, String documentId) {
        requireAdmin(adminId);
        var user = targetUser(userId);
        return markdownDocuments.get(user.getId(), documentId);
    }

    @Transactional(readOnly = true)
    public MarkdownImageService.ImageContent markdownImage(String adminId, String userId, String imageId) {
        requireAdmin(adminId);
        var user = targetUser(userId);
        return markdownImages.read(user.getId(), imageId);
    }

    @Transactional(readOnly = true)
    public AdminWorkspaceDtos.Account memberAccount(String adminId, String userId) {
        requireAdmin(adminId);
        return account(targetUser(userId));
    }

    private void requireAdmin(String adminId) {
        var admin = users.findById(adminId)
                .orElseThrow(() -> new ForbiddenException("需要管理员权限"));
        if (!admin.isEnabled() || admin.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("需要管理员权限");
        }
    }

    private UserEntity targetUser(String userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("账户", userId));
    }

    private AdminWorkspaceDtos.Account account(UserEntity user) {
        return new AdminWorkspaceDtos.Account(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(),
                user.isEnabled(), user.getCreatedAt());
    }
}
