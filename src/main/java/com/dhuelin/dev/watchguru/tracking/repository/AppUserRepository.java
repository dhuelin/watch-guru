package com.dhuelin.dev.watchguru.tracking.repository;

import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByAuthSubject(String authSubject);
}
