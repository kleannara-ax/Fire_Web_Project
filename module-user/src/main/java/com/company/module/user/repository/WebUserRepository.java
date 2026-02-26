package com.company.module.user.repository;

import com.company.module.user.entity.WebUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 웹 사용자 Repository
 */
public interface WebUserRepository extends JpaRepository<WebUser, Long> {

    Optional<WebUser> findByUsername(String username);

    Optional<WebUser> findByUsernameAndActiveTrue(String username);

    boolean existsByUsername(String username);
}
