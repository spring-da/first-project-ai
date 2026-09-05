package com.springda.devnest.admin;

import com.springda.devnest.knowledge.KnowledgeDomainDtos;
import com.springda.devnest.log.DevLogDtos;
import com.springda.devnest.markdown.MarkdownDocumentDtos;
import com.springda.devnest.profile.ProfileDtos;
import com.springda.devnest.project.ProjectDtos;
import com.springda.devnest.snippet.SnippetDtos;
import com.springda.devnest.task.TaskDtos;
import com.springda.devnest.user.UserRole;

import java.time.Instant;
import java.util.List;

public final class AdminWorkspaceDtos {

    private AdminWorkspaceDtos() {
    }

    public record Account(
            String id,
            String email,
            String displayName,
            UserRole role,
            boolean enabled,
            Instant registeredAt
    ) {
    }

    public record Snapshot(
            Account account,
            ProfileDtos.Response profile,
            List<KnowledgeDomainDtos.Response> domains,
            List<TaskDtos.Response> tasks,
            List<ProjectDtos.Response> projects,
            List<MarkdownDocumentDtos.SummaryResponse> markdownDocuments,
            List<SnippetDtos.Response> snippets,
            List<DevLogDtos.Response> logs
    ) {
    }
}
