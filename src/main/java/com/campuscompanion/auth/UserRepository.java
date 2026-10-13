package com.campuscompanion.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  /** The email must already be normalised: trimmed and lower case. */
  Optional<User> findByLoginEmail(String loginEmail);

  boolean existsByLoginEmail(String loginEmail);

  boolean existsByRole(Role role);

  Optional<User> findFirstByRoleOrderByIdAsc(Role role);
}
