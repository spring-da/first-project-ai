package com.springda.devnest.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, String> {

    Optional<UserEntity> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from UserEntity user where lower(user.email) = lower(:email)")
    Optional<UserEntity> findByEmailIgnoreCaseForUpdate(@Param("email") String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByDisplayNameIgnoreCase(String displayName);

    boolean existsByDisplayNameKey(String displayNameKey);

    boolean existsByDisplayNameIgnoreCaseAndIdNot(String displayName, String id);

    boolean existsByDisplayNameKeyAndIdNot(String displayNameKey, String id);

}
