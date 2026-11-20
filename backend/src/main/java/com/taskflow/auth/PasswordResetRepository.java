package com.taskflow.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface PasswordResetRepository extends JpaRepository<PasswordResetToken, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from PasswordResetToken t where t.tokenHash=:hash")
  Optional<PasswordResetToken> lockByHash(String hash);

  @Modifying
  @Query("update PasswordResetToken t set t.usedAt=:now where t.user.id=:id and t.usedAt is null")
  void invalidateUser(UUID id, Instant now);
}
