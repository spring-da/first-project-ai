package com.springda.devnest.community;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CommunityMessageRepository extends JpaRepository<CommunityMessageEntity, String> {
    List<CommunityMessageEntity> findByParentIdIsNullOrderByCreatedAtDescIdDesc(Pageable pageable);

    @Query("""
            select message from CommunityMessageEntity message
            where message.parentId is null
              and (message.createdAt < (
                    select boundary.createdAt from CommunityMessageEntity boundary
                    where boundary.id = :cursorId and boundary.parentId is null
                  )
                or (message.createdAt = (
                    select boundary.createdAt from CommunityMessageEntity boundary
                    where boundary.id = :cursorId and boundary.parentId is null
                  ) and message.id < :cursorId))
            order by message.createdAt desc, message.id desc
            """)
    List<CommunityMessageEntity> findRootMessagesBefore(
            @Param("cursorId") String cursorId,
            Pageable pageable
    );

    Slice<CommunityMessageEntity> findByParentIdOrderByCreatedAtAscIdAsc(String parentId, Pageable pageable);

    interface ReplyCount {
        String getParentId();
        long getReplyCount();
    }

    @Query("""
            select message.parentId as parentId, count(message.id) as replyCount
            from CommunityMessageEntity message
            where message.parentId in :parentIds
            group by message.parentId
            """)
    List<ReplyCount> countReplies(@Param("parentIds") Collection<String> parentIds);

    @Query("""
            select message.imageObjectKey from CommunityMessageEntity message
            where message.imageObjectKey is not null
              and (message.id = :messageId or message.parentId = :messageId)
            """)
    List<String> findImageObjectKeysForThread(@Param("messageId") String messageId);

    @Query("""
            select message.imageObjectKey from CommunityMessageEntity message
            where message.imageObjectKey is not null and (
                message.authorId = :authorId or message.parentId in (
                    select root.id from CommunityMessageEntity root
                    where root.parentId is null and root.authorId = :authorId
                )
            )
            """)
    List<String> findImageObjectKeysRemovedWithAuthor(@Param("authorId") String authorId);
}
