package com.taskflow.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from RefreshToken t where t.tokenHash=:hash")
  Optional<RefreshToken> lockByHash(String hash);

  @Modifying
  @Query("update RefreshToken t set t.revokedAt=:now where t.familyId=:family")
  void revokeFamily(UUID family, Instant now);

  @Modifying
  @Query("update RefreshToken t set t.revokedAt=:now where t.user.id=:userId")
  void revokeUser(UUID userId, Instant now);
}
