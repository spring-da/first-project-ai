package com.springda.devnest.sharing;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ShareBundleItemRepository extends JpaRepository<ShareBundleItemEntity, String> {
    Optional<ShareBundleItemEntity> findByIdAndBundleId(String id, String bundleId);
}
