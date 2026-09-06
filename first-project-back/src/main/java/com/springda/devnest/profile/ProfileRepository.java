package com.springda.devnest.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface ProfileRepository extends JpaRepository<ProfileEntity, String> {

    Optional<ProfileEntity> findByOwnerId(String ownerId);

    List<ProfileEntity> findAllByOwnerIdIn(Collection<String> ownerIds);
}
