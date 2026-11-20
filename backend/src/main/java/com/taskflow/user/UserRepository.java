package com.taskflow.user;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
  Optional<User> findByEmail(String email);

  Optional<User> findByUsername(String username);

  boolean existsByEmail(String email);

  boolean existsByUsername(String username);

  long countBySystemRoleAndActiveTrue(SystemRole role);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.systemRole=com.taskflow.user.SystemRole.ADMIN order by u.id")
  List<User> lockAdministrators();

  boolean existsByIdAndSystemRoleAndActiveTrue(UUID id, SystemRole role);

  @Query(
      "select u from User u where u.active = true and (lower(u.fullName) like lower(concat('%',:q,'%')) or lower(u.email) like lower(concat('%',:q,'%')))")
  Page<User> search(String q, Pageable pageable);
}
