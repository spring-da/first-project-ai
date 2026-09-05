package com.springda.devnest.snippet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SnippetRepository extends JpaRepository<SnippetEntity, String> {

    List<SnippetEntity> findAllByOwnerIdAndDeletedAtIsNullOrderByFavoriteDescUpdatedAtDesc(String ownerId);

    List<SnippetEntity> findAllByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(String ownerId);

    Optional<SnippetEntity> findByIdAndOwnerId(String id, String ownerId);
}
