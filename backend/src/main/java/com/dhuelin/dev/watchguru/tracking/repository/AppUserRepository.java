package com.dhuelin.dev.watchguru.tracking.repository;

import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByAuthSubject(String authSubject);

    /**
     * Hard-deletes the user with a single statement, letting the database
     * cascade through watchlist items, episode watches, watch events and linked
     * accounts.
     *
     * <p>Deliberately not {@code JpaRepository.deleteById}: that loads the
     * entity and then deletes only what JPA knows about, which here is the user
     * row alone. Every child table declares {@code ON DELETE CASCADE} in
     * {@code V1__initial_schema.sql}, so a direct delete is both correct and one
     * round trip rather than several. Account deletion has to actually remove
     * the data -- both app stores require it, and a half-deleted account is
     * worse than none.
     */
    @Modifying
    @Query("delete from AppUser u where u.id = :id")
    int deleteAppUserById(@Param("id") Long id);
}
