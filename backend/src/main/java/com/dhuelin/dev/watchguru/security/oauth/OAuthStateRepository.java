package com.dhuelin.dev.watchguru.security.oauth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface OAuthStateRepository extends JpaRepository<OAuthState, String> {

    @Modifying
    @Query("delete from OAuthState s where s.expiresAt < :before")
    int deleteExpired(@Param("before") Instant before);
}
