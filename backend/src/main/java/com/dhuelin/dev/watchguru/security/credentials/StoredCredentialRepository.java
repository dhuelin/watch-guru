package com.dhuelin.dev.watchguru.security.credentials;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoredCredentialRepository extends JpaRepository<StoredCredential, Long> {

    Optional<StoredCredential> findByCredentialRef(String credentialRef);

    void deleteByCredentialRef(String credentialRef);
}
