package com.dhuelin.dev.watchguru.notifications.repository;

import com.dhuelin.dev.watchguru.notifications.domain.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByUserId(Long userId);

    void deleteByToken(String token);

    void deleteByUserIdAndToken(Long userId, String token);

    /**
     * Everybody the scan could possibly notify.
     *
     * <p>Driving the scan off registered devices rather than off users means a
     * user who has never opened the app on a phone costs nothing at all, which
     * is most of the table on any given hour.
     */
    @Query("select distinct d.user.id from DeviceToken d")
    List<Long> findUserIdsWithDevices();
}
