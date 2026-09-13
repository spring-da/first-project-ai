package com.springda.devnest.sharing;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface ShareBundleRepository extends JpaRepository<ShareBundleEntity, String> {
    Optional<ShareBundleEntity> findByIdAndOwnerId(String id, String ownerId);
    Optional<ShareBundleEntity> findByTokenDigest(String digest);
    List<ShareBundleEntity> findAllByOwnerIdAndIdIn(String ownerId, List<String> ids);
}
