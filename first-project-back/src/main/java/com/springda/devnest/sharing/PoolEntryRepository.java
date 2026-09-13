package com.springda.devnest.sharing;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface PoolEntryRepository extends JpaRepository<PoolEntryEntity, String> {
    Optional<PoolEntryEntity> findByOwnerIdAndResourceTypeAndResourceId(String ownerId, ResourceType type, String resourceId);
    List<PoolEntryEntity> findAllByOwnerIdAndIdIn(String ownerId, List<String> ids);
}
