package com.springda.devnest.image;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface MarkdownImageRepository extends JpaRepository<MarkdownImageEntity, String> {
    Optional<MarkdownImageEntity> findByIdAndOwnerId(String id, String ownerId);
    List<MarkdownImageEntity> findAllByOwnerId(String ownerId);
    List<MarkdownImageEntity> findAllByOwnerIdAndIdIn(String ownerId, Collection<String> ids);
}
