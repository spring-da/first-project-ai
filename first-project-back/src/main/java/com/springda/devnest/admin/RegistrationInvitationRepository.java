package com.springda.devnest.admin;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RegistrationInvitationRepository extends JpaRepository<RegistrationInvitationEntity, String> {

    Optional<RegistrationInvitationEntity> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from RegistrationInvitationEntity invitation where lower(invitation.email) = lower(:email)")
    Optional<RegistrationInvitationEntity> findByEmailIgnoreCaseForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from RegistrationInvitationEntity invitation where invitation.tokenHash = :tokenHash")
    Optional<RegistrationInvitationEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    void deleteByRegisteredUserId(String registeredUserId);
}
