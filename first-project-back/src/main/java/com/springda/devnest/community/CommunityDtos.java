package com.springda.devnest.community;

import com.springda.devnest.user.UserRole;

import java.time.Instant;
import java.util.List;

public final class CommunityDtos {
    private CommunityDtos() {
    }

    public record MessageResponse(
            String id,
            String parentId,
            String authorId,
            String authorName,
            UserRole authorRole,
            String content,
            String imageUrl,
            long replyCount,
            boolean viewerCanDelete,
            Instant createdAt
    ) {
    }

    public record MessagePage(List<MessageResponse> items, String nextCursor) {
    }

    public record ReplyPage(List<MessageResponse> items, Integer nextPage) {
    }
}
